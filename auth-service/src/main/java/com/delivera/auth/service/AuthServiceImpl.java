package com.delivera.auth.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.delivera.auth.builder.CredentialBuilder;
import com.delivera.auth.dto.DeliveraOrgContext;
import com.delivera.auth.dto.LoginResponse;
import com.delivera.auth.dto.RefreshCookieData;
import com.delivera.auth.exception.EmailAlreadyExistsException;
import com.delivera.auth.exception.ForbiddenException;
import com.delivera.auth.exception.InvalidCredentialsException;
import com.delivera.auth.exception.UserNotFoundException;
import com.delivera.auth.exception.UsernameAlreadyExistsException;
import com.delivera.auth.model.Credential;
import com.delivera.auth.repository.CredentialRepository;
import com.delivera.auth.security.AuthRateLimiter;
import com.delivera.auth.security.InMemoryAuthRateLimiter;
import com.delivera.auth.security.jwt.JwtService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimiter rateLimiter;
    private final JwtService jwtService;

    @Autowired
    public AuthServiceImpl(CredentialRepository credentialRepository,
                           PasswordEncoder passwordEncoder, 
                           InMemoryAuthRateLimiter inMemoryAuthRateLimiter,
                           JwtService jwtService
                           ) {
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = inMemoryAuthRateLimiter;
        this.jwtService = jwtService;

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
        if (username != null && credentialRepository.findByUsername(username).isPresent()) {
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
    public Credential createCredentials(UUID userId, String email, String username, String password) {
        
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

        return create(credential);
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

    public void avoidAttacksWithCorrectIdentifier(String password, Credential credential, String ip) {
        // TO AVOID Brute force and Credential Snuffing 
        try {
            checkPassword(password,credential);
        }catch(InvalidCredentialsException e) {
            rateLimiter.check("FAIL_USER:" + credential.getEmail());
            rateLimiter.check("FAIL_IP:" + ip);

            throw e;
        }
    }

    public void checkPassword(String password, Credential credential) {
        if (!passwordEncoder.matches(password, credential.getPasswordHash())) {
            throw new  InvalidCredentialsException();
        }
    }

    public void checkTokenVersion(Integer tokenVersion, Credential credential) {
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
    public Credential changePassword(Credential credential, String rawPreviousPassword, String rawNewPassword, Integer tokenVersion, String ip) {
        avoidAttacksWithCorrectIdentifier(rawPreviousPassword,credential,ip);
        checkTokenVersion(tokenVersion, credential);
        credential.setPasswordHash(passwordEncoder.encode(rawNewPassword));
        credential.setTokenVersion(credential.getTokenVersion()+1);
       
        return credentialRepository.save(credential);


    }

    

    public LoginResponse buildLoginResponse(Credential credential, DeliveraOrgContext orgInfo, RefreshCookieData refreshCookie) {
        orgInfo = orgInfo == null? new DeliveraOrgContext() : orgInfo;
        String token = jwtService.generateToken(
            credential.getUserId(),
            credential.getEmail(), 
            orgInfo.getCompanyId() , 
            orgInfo.getRole(),
            orgInfo.getOrgId(),
            credential.getTokenVersion()
        );

        return new LoginResponse(
            token, 
            credential.getEmail(),
            orgInfo.getCompanyId(), 
            orgInfo.getRole(), 
            orgInfo.getCompanyName(), 
            orgInfo.getOrgHandle(), 
            orgInfo.getOrgName(),
            refreshCookie
        );
        
    }

    public LoginResponse buildLoginResponse(Credential credential, DeliveraOrgContext orgInfo) {
        return buildLoginResponse(credential, orgInfo, null);
    }


     @Override
     @Transactional
     public Credential changeUsername(UUID userId, String username) {
        Credential credential = credentialRepository.findById(userId)
        .orElseThrow(() -> new ForbiddenException("YOU CAN'T DO THIS OPERATION"));
        checkIUsernameExists(username);
        credential.setUsername(username);
        return credentialRepository.save(credential);
     }


     @Override
     @Transactional
     public void delete(UUID userId) {
        Optional<Credential> credential = credentialRepository.findByUserId(userId);
        if (credential.isPresent()) {
            credentialRepository.delete(credential.get());
        }
     }


     @Override
     @Transactional
     public void deleteUsers(Set<UUID> userIds) {
        if (!userIds.isEmpty()) {
            credentialRepository.deleteUsers(userIds);
        }
     }

     @Override
     @Transactional
     public void deleteAllExceptUserId(UUID userId) {
        credentialRepository.deleteAllExceptUserId(userId);
      
     }
   
}
