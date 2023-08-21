package com.lumen.vertx.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import java.io.Serializable;

@Entity
public class Request implements Serializable
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emp_id")
    private String empId;

    private String firstName;
    private String lastName;
    private String ssn;
    private String dob;
    private String justification;

    public Request() {
    }

    public Request(String empId, String firstName, String lastName, String ssn, String dob, String justification) {
        this.empId = empId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.ssn = ssn;
        this.dob = dob;
        this.justification = justification;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmpId() {
        return this.empId;
    }

    public void setEmpId(String empId) {
        this.empId = empId;
    }

    public String getFirstName() {
        return this.firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return this.lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getSsn() {
        return this.ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public String getDob() {
        return this.dob;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public String getJustification() {
        return this.justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    @Override
    public String toString() {
        return "Request{" +
                "id=" + id +
                ", empId='" + empId + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", ssn='" + ssn + '\'' +
                ", dob='" + dob + '\'' +
                ", justification='" + justification + '\'' +
                '}';
    }

}
