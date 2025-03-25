# InBank Backend Service

This service provides a REST API for calculating an approved loan amount and period for a customer.
The loan amount is calculated based on the customer's credit modifier, which is determined by the last four
digits of their ID code.

## Technologies Used

- Java 17
- Spring Boot
- [estonian-personal-code-validator:1.6](https://github.com/vladislavgoltjajev/java-personal-code)

## Requirements

- Java 17
- Gradle

## Installation

To install and run the service, please follow these steps:

1. Clone the repository.
2. Navigate to the root directory of the project.
3. Run `gradle build` to build the application.
4. Run `java -jar build/libs/inbank-backend-1.0.jar` to start the application

The default port is 8080.

## Endpoints

The application exposes a single endpoint:

### POST /loan/decision

The request body must contain the following fields:

- personalCode: The customer's personal ID code.
- loanAmount: The requested loan amount.
- loanPeriod: The requested loan period.
- country: The cunstomer's residence

**Request example:**

```json
{
"personalCode": "50307172740",
"loanAmount": "5000",
"loanPeriod": "24",
"country": "Estonia"
}
```

The response body contains the following fields:

- loanAmount: The approved loan amount.
- loanPeriod: The approved loan period.
- errorMessage: An error message, if any.

**Response example:**

```json
{
"loanAmount": 2400,
"loanPeriod": 24,
"errorMessage": null
}
```

## Loan Decision Logic

The decision engine uses the following business rules:

### ✅ Credit Modifier

A customer's **credit modifier** is determined by the **last four digits** of their personal code:

- `0000–2499` → ❌ Denied (customer has debt).
- `2500–4999` → Segment 1 → Modifier: 100
- `5000–7499` → Segment 2 → Modifier: 300
- `7500–9999` → Segment 3 → Modifier: 1000

### ✅ Credit Score Calculation

To determine loan eligibility, the system calculates the **credit score**:

```
creditScore = ((creditModifier / loanAmount) * loanPeriod) / 10
```

- If `creditScore ≥ 0.1` → ✅ Approved
- If `creditScore < 0.1` → ❌ Try alternative loan period (see below)

### ✅ Loan Period Adjustment

If the credit score is too low for the requested period, the system will **automatically increase the loan period** (up to 48 months) to try and **find a valid credit score and maximum possible loan**.

- The system always returns the **maximum amount** the customer is eligible for.
- If no valid configuration is found, the request is rejected.

### ✅ Age-Based Restrictions

The service also validates the customer's age using the personal code and provided country:

- **Minimum age**: 18 years.
- **Maximum age**: Calculated as:

```
maxAllowedAge = (lifeExpectancy of country) - (loanPeriod in years)
```

If the customer is:
- Younger than 18 → ❌ Rejected.
- Older than the maximum allowed → ❌ Rejected.

### Country-specific life expectancies:

| Country     | Life Expectancy |
|-------------|-----------------|
| Estonia     | 78              |
| Latvia      | 75              |
| Lithuania   | 76              |
| Default     | 82              |

> Note: Only Baltic countries are supported for age validation.

## Error Handling

The following error responses can be returned by the service:

- `400 Bad Request` - in case of an invalid input
    - `Invalid personal ID code!` - if the provided personal ID code is invalid
    - `Invalid loan amount!` - if the requested loan amount is invalid
    - `Invalid loan period!` - if the requested loan period is invalid
- `404 Not Found` - in case no valid loans can be found
    - `No valid loan found!` - if there is no valid loan found for the given ID code, loan amount, and loan period
- `500 Internal Server Error` - in case the server encounters an unexpected error while processing the request
    - `An unexpected error occurred` - if there is an unexpected error while processing the request

## Architecture

The service consists of two main classes:

- DecisionEngine: A service class that provides a method for calculating an approved loan amount and period for a customer.
- DecisionEngineController: A REST endpoint that handles requests for loan decisions.
