package com.delivera.auth.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.auth.builder.CredentialBuilder;
import com.delivera.auth.exception.EmailAlreadyExistsException;
import com.delivera.auth.exception.ForbiddenException;
import com.delivera.auth.exception.InvalidCredentialsException;
import com.delivera.auth.exception.UserNotFoundException;
import com.delivera.auth.exception.UsernameAlreadyExistsException;
import com.delivera.auth.model.Credential;
import com.delivera.auth.repository.CredentialRepository;
import com.delivera.auth.security.AuthRateLimiter;
import com.delivera.auth.security.InMemoryAuthRateLimiter;


import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimiter rateLimiter;

    @Autowired
    public AuthServiceImpl(CredentialRepository credentialRepository,
                           PasswordEncoder passwordEncoder, 
                           InMemoryAuthRateLimiter inMemoryAuthRateLimiter
                           ) {
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = inMemoryAuthRateLimiter;

    }

    
    private void checkIfEmailExists(String email) throws EmailAlreadyExistsException{
        if (email == null) {
            throw new InvalidCredentialsException();
        }
        if (credentialRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException();
        }
    }

    private void checkIUsernameExists(String username) throws UsernameAlreadyExistsException{
        if (username == null) {
            throw new InvalidCredentialsException();
        }
        if (credentialRepository.findByUsername(username).isPresent()) {
            throw new UsernameAlreadyExistsException();
        }
    }

    private void checkIfUUIDExists(UUID uuid) throws InvalidCredentialsException {
        if (uuid == null) {
            throw new InvalidCredentialsException();
        }
        if (credentialRepository.findByUserId(uuid).isPresent()) {
            throw new InvalidCredentialsException();
        }
    }

    private Credential create(Credential credential) {
        Credential newCredential = null;
        try {
            newCredential =  credentialRepository.save(credential);

            log.info("Creating credentials for userId={}, email={}", credential.getUserId(), credential.getEmail());
        } catch (DataIntegrityViolationException exception) {
            log.warn("Unexpected error creating credentials for userId={}", credential.getUserId(), exception);
            checkIfEmailExists(credential.getEmail());
            checkIUsernameExists(credential.getUsername());
            checkIfUUIDExists(credential.getUserId());
            
            log.error("Unexpected DB error creating credentials", exception);
            throw new RuntimeException("Unexpected database error", exception);

        }
        return newCredential;
    }

    @Override
    @Transactional
    public void createCredentials(UUID userId, String email, String username, String password) {
        
        // CHECK IN CREATE AND HERE TO GET A BETTER UX
        checkIfUUIDExists(userId);
        checkIfEmailExists(email);
        checkIUsernameExists(username);

        Credential credential = CredentialBuilder.of()
                                .userId(userId)
                                .email(email)
                                .username(username)
                                .passwordHash(passwordEncoder.encode(password))
                                .tokenVersion(0)
                                .build();

        create(credential);
    }

    private void avoidGlobalAttacks(String identifier, String ip) {
        // TO AVOID DDos attacks and Brute force and distributed Brute force
        rateLimiter.check("IP:" + ip);
        rateLimiter.check("USER_IP:" + identifier + ":" + ip);
        

    }


    private Credential avoidFailUserAttacks(String identifier, String ip) {
        // TO AVOID User enumeration and Credential Snuffing 
        try {
            return getCredentialByIdentifier(identifier);
        } catch (InvalidCredentialsException e) {
            rateLimiter.check("FAIL_USER:" + identifier);
            rateLimiter.check("FAIL_IP:" + ip);

            throw e;
        }
    }

    private void avoidAttacksWithCorrectIdentifier(String password, Credential credential, String ip) {
        // TO AVOID Brute force and Credential Snuffing 
        try {
            checkPassword(password,credential);
        }catch(InvalidCredentialsException e) {
            rateLimiter.check("FAIL_USER:" + credential.getEmail());
            rateLimiter.check("FAIL_IP:" + ip);

            throw e;
        }
    }

    private void checkPassword(String password, Credential credential) {
        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            throw new  InvalidCredentialsException();
        }
    }

    private void checkTokenVersion(Integer tokenVersion, Credential credential) {
        if (!tokenVersion.equals(credential.getTokenVersion())) {
            throw new  InvalidCredentialsException();
        }
    }

    private Credential getCredentialByIdentifier(String identifier) {
        return credentialRepository
        .findByEmailIgnoreCaseOrUsernameIgnoreCase(identifier, identifier)
        .orElseThrow(() -> new InvalidCredentialsException());
    }


    @Override
    @Transactional(readOnly = true)
    public Credential login(String identifier, String password, String ip) {
        avoidGlobalAttacks(identifier, ip);
        Credential credential = avoidFailUserAttacks(identifier, ip);
        avoidAttacksWithCorrectIdentifier(password, credential, ip);
        // AVOID INCORRECT LOGIN USE
        rateLimiter.check("USER:" + identifier);
        return credential;
    }


    @Override
    @Transactional(readOnly = true)
    public Credential getUserCredentialByEmail(String email, Integer tokenVersion) {
        Credential credential =  credentialRepository.findByEmail(email)
        .orElseThrow(() -> new UserNotFoundException());
        checkTokenVersion(tokenVersion, credential);
        return credential;
    }


    @Override
    @Transactional
    public Credential changePassword(UUID userId, String rawPreviousPassword, String rawNewPassword, Integer tokenVersion) {
        Credential credential = credentialRepository.findById(userId)
        .orElseThrow(() -> new ForbiddenException("YOU CAN'T DO THIS OPERATION"));
        checkPassword(rawPreviousPassword, credential);
        checkTokenVersion(tokenVersion, credential);
        credential.setPasswordHash(passwordEncoder.encode(rawNewPassword));
        credential.setTokenVersion(credential.getTokenVersion()+1);
       
        return credentialRepository.save(credential);


    }




   
}
