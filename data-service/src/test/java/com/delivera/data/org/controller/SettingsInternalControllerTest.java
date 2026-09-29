package com.delivera.data.org.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.delivera.data.org.dto.CompanySettingsDTO;
import com.delivera.data.org.model.CompanySettings;
import com.delivera.data.org.service.SettingsService;

@ExtendWith (MockitoExtension.class)
class SettingsInternalControllerTest {

    @Mock 
    private SettingsService settingsService;

    @InjectMocks 
    private SettingsInternalController controller;

    private UUID companyId;
    private UUID orgId;

    @BeforeEach 
    void setUp() {
        companyId = UUID.randomUUID();
        orgId = UUID.randomUUID();
    }
    @Test
    void createSeed_shouldReturnOk() {

        CompanySettingsDTO request =
                new CompanySettingsDTO();

        ResponseEntity<Void> response =
                controller.createSeed(request);

        verify(settingsService)
                .create(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
    @Test
    void create_shouldReturnCreatedAndBody() {

        CompanySettingsDTO request =
                new CompanySettingsDTO();

        CompanySettings settings =
                mock(CompanySettings.class);

        CompanySettingsDTO responseDto =
                new CompanySettingsDTO();

        when(settingsService.create(request))
                .thenReturn(settings);

        when(settings.dto())
                .thenReturn(responseDto);

        ResponseEntity<CompanySettingsDTO> response =
                controller.create(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getBody())
                .isSameAs(responseDto);
    }
    @Test
    void deleteCompany_shouldReturnNoContent() {
    
        ResponseEntity<Void> response =
                controller.deleteCompany(
                        true,
                        companyId
                );
    
        verify(settingsService)
                .deleteCompany(
                        companyId,
                        true
                );
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
    @Test
    void deleteOrganization_shouldReturnNoContent() {
    
        Set<UUID> companyIds =
                Set.of(
                        UUID.randomUUID(),
                        UUID.randomUUID()
                );
    
        ResponseEntity<Void> response =
                controller.deleteOrganization(
                        companyIds,
                        orgId
                );
    
        verify(settingsService)
                .deleteOrganization(
                        orgId.toString(),
                        companyIds
                );
    
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }
}
