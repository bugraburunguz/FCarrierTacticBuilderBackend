package com.fcareer.billing.model.response;

/** Ücretli işlerin kredi bedelleri (premium üyeler için bedava); UI "kaç kredi düşer"i buradan okur. */
public record CreditCostsResponse(int recommend, int squadFit, int advisor) {
}
