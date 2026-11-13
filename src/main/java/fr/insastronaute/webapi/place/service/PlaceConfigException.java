package fr.insastronaute.webapi.place.service;

import fr.insastronaute.webapi.common.web.CodedBadRequestException;

public class PlaceConfigException extends CodedBadRequestException {

    public PlaceConfigException(String code, String message) {
        super(code, message);
    }
}
