package com.ecommerce.project.service.impl;

import com.ecommerce.project.service.TotpService;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.stereotype.Service;

@Service
public class TotpServiceSimpl implements TotpService {

    private final GoogleAuthenticator gAuth;

    public TotpServiceSimpl(GoogleAuthenticator gAuth) {
        this.gAuth = gAuth;
    }
    public TotpServiceSimpl() {
        this.gAuth = new GoogleAuthenticator();
    }

    @Override
    public GoogleAuthenticatorKey generateSecret(){
        return gAuth.createCredentials();
    }
    @Override
    public String getQrCodeUrl(GoogleAuthenticatorKey secret,String username){
        return GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL("E-commerce Application",username,secret);
    }

    @Override
    public boolean verifyCode(String secret,int code){
        return gAuth.authorize(secret,code);
    }
}
