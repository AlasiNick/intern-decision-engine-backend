package ee.taltech.inbankbackend.service;

import com.github.vladislavgoltjajev.personalcode.locale.estonia.EstonianPersonalCodeValidator;
import ee.taltech.inbankbackend.config.DecisionEngineConstants;
import ee.taltech.inbankbackend.exceptions.InvalidLoanAmountException;
import ee.taltech.inbankbackend.exceptions.InvalidLoanPeriodException;
import ee.taltech.inbankbackend.exceptions.InvalidPersonalCodeException;
import ee.taltech.inbankbackend.exceptions.NoValidLoanException;
import ee.taltech.inbankbackend.exceptions.InvalidAgeException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;

/**
 * A service class that provides a method for calculating an approved loan amount and period for a customer.
 * The loan amount is calculated based on the customer's credit modifier,
 * which is determined by the last four digits of their ID code.
 */
@Service
public class DecisionEngine {

    // Used to check for the validity of the presented ID code.
    private final EstonianPersonalCodeValidator validator = new EstonianPersonalCodeValidator();
    private int creditModifier = 0;

    /**
     * Calculates the maximum loan amount and period for the customer based on their ID code,
     * the requested loan amount and the loan period.
     * The loan period must be between 12 and 48 months (inclusive).
     * The loan amount must be between 2000 and 10000€ (inclusive).
     * The customer must be at least 18 years old and not older than the expected lifetime for their country.
     *
     * @param personalCode ID code of the customer that made the request.
     * @param requestedLoanAmount Requested loan amount
     * @param requestedLoanPeriod Requested loan period
     * @param country The country of the customer
     * @return A Decision object containing the approved loan amount and period, and an error message (if any)
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException If the requested loan period is invalid
     * @throws NoValidLoanException If there is no valid loan found for the given ID code, loan amount and loan period
     * @throws InvalidAgeException If the customer's age is outside the allowed range
     */
    public Decision calculateApprovedLoan(String personalCode, Long requestedLoanAmount, int requestedLoanPeriod, String country)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException,
            NoValidLoanException, InvalidAgeException {

        creditModifier = getCreditModifier(personalCode);
        if (creditModifier == 0) {
            throw new NoValidLoanException("Loan denied due to existing debt.");
        }

        verifyInputs(personalCode, requestedLoanAmount, requestedLoanPeriod);
        checkAgeRestrictions(personalCode, requestedLoanPeriod, country);

        double creditScore = calculateCreditScore(creditModifier, requestedLoanAmount, requestedLoanPeriod);

        if (creditScore < 0.1) {
            return findAlternativeLoanOption(requestedLoanPeriod);
        }

        int outputLoanAmount = Math.min(DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT,
                calculateLoanAmount(requestedLoanPeriod));

        return new Decision(outputLoanAmount, requestedLoanPeriod, null);
    }

    /**
     * Searches for an alternative loan period that meets the minimum required credit score.
     *
     * @param requestedLoanPeriod The initially requested loan period.
     * @return A Decision object with an alternative loan period and amount.
     * @throws NoValidLoanException If no valid loan option is found.
     */
    private Decision findAlternativeLoanOption(int requestedLoanPeriod) throws NoValidLoanException {
        for (int period = requestedLoanPeriod + 1; period <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD; period++) {
            int alternativeLoanAmount = calculateLoanAmount(period);
            if (alternativeLoanAmount >= DecisionEngineConstants.MINIMUM_LOAN_AMOUNT &&
                    calculateCreditScore(creditModifier, (long) alternativeLoanAmount, period) >= 0.1) {
                return new Decision(alternativeLoanAmount, period, null);
            }
        }
        throw new NoValidLoanException("No valid loan found within the allowed period.");
    }

    /**
     * Calculates the loan amount based on credit modifier and loan period.
     *
     * @param loanPeriod Loan period in months.
     * @return Maximum possible loan amount.
     */
    private int calculateLoanAmount(int loanPeriod) {
        return creditModifier * loanPeriod;
    }

    /**
     * Calculates the credit score.
     *
     * @param creditModifier Customer's credit modifier.
     * @param loanAmount     Requested loan amount.
     * @param loanPeriod     Requested loan period.
     * @return Calculated credit score.
     */
    private double calculateCreditScore(int creditModifier, Long loanAmount, int loanPeriod) {
        return ((double) creditModifier / loanAmount) * loanPeriod / 10;
    }

    /**
     * Calculates the credit modifier of the customer to according to the last four digits of their ID code.
     * Debt - 0000...2499
     * Segment 1 - 2500...4999
     * Segment 2 - 5000...7499
     * Segment 3 - 7500...9999
     *
     * @param personalCode ID code of the customer that made the request.
     * @return Segment to which the customer belongs.
     */
    private int getCreditModifier(String personalCode) {
        int segment = Integer.parseInt(personalCode.substring(personalCode.length() - 4));

        if (segment < 2500) {
            return 0;
        } else if (segment < 5000) {
            return DecisionEngineConstants.SEGMENT_1_CREDIT_MODIFIER;
        } else if (segment < 7500) {
            return DecisionEngineConstants.SEGMENT_2_CREDIT_MODIFIER;
        }

        return DecisionEngineConstants.SEGMENT_3_CREDIT_MODIFIER;
    }

    /**
     * Validates that all inputs meet business requirements.
     *
     * @param personalCode The customer's personal ID code.
     * @param loanAmount The requested loan amount.
     * @param loanPeriod The requested loan period.
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid.
     * @throws InvalidLoanAmountException If the requested loan amount is out of bounds.
     * @throws InvalidLoanPeriodException If the requested loan period is out of bounds.
     */
    private void verifyInputs(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException {
        if (!validator.isValid(personalCode)) {
            throw new InvalidPersonalCodeException("Invalid personal ID code.");
        }
        if (loanAmount < DecisionEngineConstants.MINIMUM_LOAN_AMOUNT || loanAmount > DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT) {
            throw new InvalidLoanAmountException("Loan amount must be between €" +
                    DecisionEngineConstants.MINIMUM_LOAN_AMOUNT + " and €" + DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT + ".");
        }
        if (loanPeriod < DecisionEngineConstants.MINIMUM_LOAN_PERIOD || loanPeriod > DecisionEngineConstants.MAXIMUM_LOAN_PERIOD) {
            throw new InvalidLoanPeriodException("Loan period must be between " +
                    DecisionEngineConstants.MINIMUM_LOAN_PERIOD + " and " + DecisionEngineConstants.MAXIMUM_LOAN_PERIOD + " months.");
        }
    }

    /**
     * Checks if the customer meets age-related restrictions.
     *
     * @param personalCode The customer's personal ID code.
     @param loanPeriod The requested loan period.
     @param country The country of the customer.
     *
     @throws InvalidAgeException If the customer is underage or too old for the requested loan period.
     @throws InvalidPersonalCodeException If the personal ID code is invalid.
     */
    private void checkAgeRestrictions(String personalCode, int loanPeriod, String country) throws InvalidAgeException, InvalidPersonalCodeException {
        LocalDate birthDate = extractBirthDate(personalCode);
        LocalDate currentDate = LocalDate.now();
        int age = Period.between(birthDate, currentDate).getYears();
        if (age < DecisionEngineConstants.MINIMUM_AGE) {
            throw new InvalidAgeException("Customer is underage and cannot receive a loan.");
        }
        int lifeExpectancy = getLifeExpectancy(country);

        int maxAcceptableAge = lifeExpectancy - (loanPeriod / 12);

        if (age > maxAcceptableAge) {
            throw new InvalidAgeException("Customer is too old to receive a loan for this period.");
        }
    }

    /**
     * Extracts the birthdate from the personal ID code.
     *
     * @param personalCode The customer's personal ID code.
     *
     * @throws InvalidPersonalCodeException If the personal ID code is invalid.
     */
    private LocalDate extractBirthDate(String personalCode) throws InvalidPersonalCodeException {
        int genderDigit = Character.getNumericValue(personalCode.charAt(0));
        int yearPrefix = switch (genderDigit) {
            case 1, 2 -> 1800;
            case 3, 4 -> 1900;
            case 5, 6 -> 2000;
            default -> throw new InvalidPersonalCodeException("Invalid personal ID format.");
        };
        int year = yearPrefix + Integer.parseInt(personalCode.substring(1, 3));
        int month = Integer.parseInt(personalCode.substring(3, 5));
        int day = Integer.parseInt(personalCode.substring(5, 7));
        return LocalDate.of(year, month, day);
    }


    /**
     * Retrieves the life expectancy based on the customer's country.
     *
     * @param country The country of the customer.
     */
    private int getLifeExpectancy(String country) {
        return switch (country.toLowerCase()) {
            case "estonia" -> DecisionEngineConstants.ESTONIA_EXPECTED_LIFETIME;
            case "latvia" -> DecisionEngineConstants.LATVIA_EXPECTED_LIFETIME;
            case "lithuania" -> DecisionEngineConstants.LITHUANIA_EXPECTED_LIFETIME;
            default -> DecisionEngineConstants.DEFAULT_EXPECTED_LIFETIME;
        };
    }

}
