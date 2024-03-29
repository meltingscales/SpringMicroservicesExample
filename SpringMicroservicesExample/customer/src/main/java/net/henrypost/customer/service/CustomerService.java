package net.henrypost.customer.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.henrypost.customer.model.jpa.Customer;
import net.henrypost.customer.model.rest.CustomerRegistrationRequest;
import net.henrypost.customer.model.rest.FraudCheckResponse;
import net.henrypost.customer.repository.CustomerRepository;
import net.henrypost.customer.util.EmailValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RestTemplate restTemplate;

    public boolean isEmailTaken(String email) {
        return !customerRepository.findCustomerByEmail(email).isEmpty();
    }

    public Customer registerCustomer(CustomerRegistrationRequest customerRegistrationRequest) {

        Customer customer = Customer
                .builder()
                .firstName(customerRegistrationRequest.firstName())
                .lastName(customerRegistrationRequest.lastName())
                .email(customerRegistrationRequest.email())
                .build();


        //valid email
        if (EmailValidator.isEmailInvalid(customer.getEmail())) {
            throw new IllegalArgumentException("Error! Email is invalid.");
        }

        //email unique
        if (isEmailTaken(customer.getEmail())) {
            //they have supplied a duplicate email
            throw new IllegalArgumentException("Error! Duplicate email: " + customer);
        }

        //store customer
        this.customerRepository.saveAndFlush(customer);

        //we must flush (commit) so we can access the customer ID and be certain that it is not null...
        //this is related to the entity lifecycle

        //check if fraudster
        FraudCheckResponse fraudCheckResponse = this.restTemplate.getForObject(
                "http://fraud/api/v1/fraud-check/{cid}",
                FraudCheckResponse.class,
                customer.getId()
        );

        assert fraudCheckResponse != null;
        if (fraudCheckResponse.isFraudster()) {
            CustomerService.log.info("warning - fraudster: {}", customer);
        }

        //todo send notification

        return customer;
    }

    public List<Customer> getAllCustomers() {
        return this.customerRepository.findAll();
    }
}
