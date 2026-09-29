package com.delivera.data.org.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.delivera.client.config.properties.SecurityUtils;
import com.delivera.data.org.dto.CompanySettingsDTO;
import com.delivera.data.org.model.CompanySettings;
import com.delivera.data.org.service.SettingsService;

@ExtendWith (MockitoExtension.class)
class SettingsControllerTest {

    @Mock 
    private SecurityUtils securityUtils;

    @Mock
    private SettingsService settingsService;

    @InjectMocks 
    private SettingsController controller;

    private UUID companyId;

    @BeforeEach 
    void setUp() {
        companyId = UUID.randomUUID();
    }
    @Test
    void get_shouldReturnCompanySettings() {

        CompanySettings settings =
                new CompanySettings();

        CompanySettingsDTO dto =
                new CompanySettingsDTO();

        settings.setId(companyId);

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        CompanySettings spy =
                Mockito.spy(settings);

        doReturn(dto)
                .when(spy)
                .dto();

        when(settingsService.get(companyId))
                .thenReturn(spy);

        ResponseEntity<CompanySettingsDTO> response =
                controller.get();

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(dto);

        verify(settingsService)
                .get(companyId);
    }
    @Test
    void update_shouldOverwriteCompanyIdAndReturnNoContent() {
    
        UUID wrongCompanyId =
                UUID.randomUUID();
    
        CompanySettingsDTO request =
                new CompanySettingsDTO();
    
        request.setCompanyId(wrongCompanyId);
    
        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);
    
        ResponseEntity<Void> response =
                controller.update(request);
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    
        assertThat(request.getCompanyId())
                .isEqualTo(companyId);
    
        verify(settingsService)
                .update(request);
    }
    @Test
    void update_shouldPassUpdatedRequestToService() {

        CompanySettingsDTO request =
                new CompanySettingsDTO();

        when(securityUtils.getCurrentCompanyId())
                .thenReturn(companyId);

        controller.update(request);

        ArgumentCaptor<CompanySettingsDTO> captor =
                ArgumentCaptor.forClass(
                        CompanySettingsDTO.class
                );

        verify(settingsService)
                .update(captor.capture());

        assertThat(
                captor.getValue()
                        .getCompanyId()
        ).isEqualTo(companyId);
    }
}