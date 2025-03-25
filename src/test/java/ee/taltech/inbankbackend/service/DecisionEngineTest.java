package ee.taltech.inbankbackend.service;

import ee.taltech.inbankbackend.config.DecisionEngineConstants;
import ee.taltech.inbankbackend.exceptions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DecisionEngineTest {

    @InjectMocks
    private DecisionEngine decisionEngine;

    private String debtorPersonalCode;
    private String segment1PersonalCode;
    private String segment2PersonalCode;
    private String segment3PersonalCode;
    private String invalidPersonalCode;

    @BeforeEach
    void setUp() {
        debtorPersonalCode = "37605030299";
        segment1PersonalCode = "50307172740";
        segment2PersonalCode = "38411266610";
        segment3PersonalCode = "35006069515";

        invalidPersonalCode = "12345678901";
    }

    /**
     * Debtor rejected.
     */
    @Test
    void testDebtorPersonalCode() {
        assertThrows(NoValidLoanException.class,
                () -> decisionEngine.calculateApprovedLoan(debtorPersonalCode, 4000L,
                        12, "Estonia"));
    }

    /**
     * Segment 1 approved.
     */
    @Test
    void testSegment1PersonalCode() throws InvalidAgeException, InvalidLoanPeriodException, NoValidLoanException,
            InvalidPersonalCodeException, InvalidLoanAmountException {
        Decision decision = decisionEngine.calculateApprovedLoan(segment1PersonalCode, 4000L,
                12, "Estonia");
        assertEquals(2000, decision.getLoanAmount());
        assertEquals(20, decision.getLoanPeriod());
    }

    /**
     * Segment 2 approved.
     * originally was 3600, 12, but since we added new logic to find alternative loan, it is now 3900, 13
     */
    @Test
    void testSegment2PersonalCode() throws InvalidAgeException, InvalidLoanPeriodException, NoValidLoanException,
            InvalidPersonalCodeException, InvalidLoanAmountException {
        Decision decision = decisionEngine.calculateApprovedLoan(segment2PersonalCode, 4000L,
                12, "Estonia");
        assertEquals(3900, decision.getLoanAmount());
        assertEquals(13, decision.getLoanPeriod());
    }

    /**
     * Segment 3 approved.
     */
    @Test
    void testSegment3PersonalCode() throws InvalidAgeException, InvalidAgeException, InvalidLoanPeriodException,
            NoValidLoanException, InvalidPersonalCodeException, InvalidLoanAmountException {
        Decision decision = decisionEngine.calculateApprovedLoan(segment3PersonalCode, 4000L,
                12, "Estonia");
        assertEquals(10000, decision.getLoanAmount());
        assertEquals(12, decision.getLoanPeriod());
    }

    /**
     * Invalid personal code, should throw an exception.
     */
    @Test
    void testInvalidPersonalCode() {
        assertThrows(InvalidPersonalCodeException.class,
                () -> decisionEngine.calculateApprovedLoan(invalidPersonalCode, 4000L,
                        12, "Estonia"));
    }

    /**
     * Invalid loan amount, should throw an exception.
     */
    @Test
    void testInvalidLoanAmount() {
        Long tooLowLoanAmount = DecisionEngineConstants.MINIMUM_LOAN_AMOUNT - 1L;
        Long tooHighLoanAmount = DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT + 1L;

        assertThrows(InvalidLoanAmountException.class,
                () -> decisionEngine.calculateApprovedLoan(segment1PersonalCode, tooLowLoanAmount,
                        12, "Estonia"));

        assertThrows(InvalidLoanAmountException.class,
                () -> decisionEngine.calculateApprovedLoan(segment1PersonalCode, tooHighLoanAmount,
                        12, "Estonia"));
    }

    /**
     * Invalid loan period, should throw an exception.
     */
    @Test
    void testInvalidLoanPeriod() {
        int tooShortLoanPeriod = DecisionEngineConstants.MINIMUM_LOAN_PERIOD - 1;
        int tooLongLoanPeriod = DecisionEngineConstants.MAXIMUM_LOAN_PERIOD + 1;

        assertThrows(InvalidLoanPeriodException.class,
                () -> decisionEngine.calculateApprovedLoan(segment1PersonalCode, 4000L,
                        tooShortLoanPeriod, "Estonia"));

        assertThrows(InvalidLoanPeriodException.class,
                () -> decisionEngine.calculateApprovedLoan(segment1PersonalCode, 4000L,
                        tooLongLoanPeriod, "Estonia"));
    }

    /**
     * Finds alternative, suitable loan period.
     */
    @Test
    void testFindSuitableLoanPeriod() throws InvalidAgeException, InvalidLoanPeriodException, NoValidLoanException,
            InvalidPersonalCodeException, InvalidLoanAmountException {
        Decision decision = decisionEngine.calculateApprovedLoan(segment2PersonalCode, 2000L,
                12, "Estonia");
        assertEquals(3600, decision.getLoanAmount());
        assertEquals(12, decision.getLoanPeriod());
    }

    /**
     * No valid loan found, should throw an exception.
     */
    @Test
    void testNoValidLoanFound() {
        assertThrows(NoValidLoanException.class,
                () -> decisionEngine.calculateApprovedLoan(debtorPersonalCode, 10000L,
                        60, "Estonia"));
    }


    /**
     * Ensure alternative loan is found if the requested loan amount is too high.
     */
    @Test
    void testAlternativeLoanSelection() throws Exception, InvalidAgeException, InvalidLoanPeriodException,
            NoValidLoanException, InvalidPersonalCodeException, InvalidLoanAmountException {
        Decision decision = decisionEngine.calculateApprovedLoan(segment2PersonalCode, 9000L,
                12, "Estonia");
        assertEquals(3900, decision.getLoanAmount());
        assertEquals(13, decision.getLoanPeriod());
    }

}

