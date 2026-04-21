package com.wing.backendapiexpensespringboot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class FirebaseAdminConfigurationPathTraversalTest {

    @Mock
    private FirebaseConfig firebaseConfig;

    @InjectMocks
    private FirebaseAdminConfiguration firebaseAdminConfiguration;

    @Test
    void pathWithParentDirectoryTraversalIsRejected() throws Exception {
        assertThatThrownBy(() -> invokeValidateAndSanitizePath("../etc/passwd.json"))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("path traversal sequences");
    }

    @Test
    void pathWithWindowsStyleTraversalIsRejected() throws Exception {
        assertThatThrownBy(() -> invokeValidateAndSanitizePath("..\\windows\\system32\\config.json"))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("path traversal sequences");
    }

    @Test
    void pathWithNullByteIsRejected() throws Exception {
        assertThatThrownBy(() -> invokeValidateAndSanitizePath("/valid/path.json\0.txt"))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("null byte");
    }

    @Test
    void pathWithUrlEncodedTraversalIsRejected() throws Exception {
        assertThatThrownBy(() -> invokeValidateAndSanitizePath("/path/%2e%2e/secret.json"))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("encoded path traversal");
    }

    @Test
    void pathWithDoubleEncodedTraversalIsRejected() throws Exception {
        assertThatThrownBy(() -> invokeValidateAndSanitizePath("/path/%252e%252e/secret.json"))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("encoded path traversal");
    }

    @Test
    void pathWithoutJsonExtensionIsRejected() throws Exception {
        assertThatThrownBy(() -> invokeValidateAndSanitizePath("/valid/path/credentials.txt"))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining(".json file");
    }

    @Test
    void emptyPathIsRejected() throws Exception {
        assertThatThrownBy(() -> invokeValidateAndSanitizePath(""))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("cannot be empty");
    }

    @Test
    void nullPathIsRejected() throws Exception {
        assertThatThrownBy(() -> invokeValidateAndSanitizePath(null))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("cannot be empty");
    }

    @Test
    void validAbsolutePathWithJsonExtensionIsAccepted() throws Exception {
        String validPath = "/opt/firebase/service-account.json";
        String result = invokeValidateAndSanitizePath(validPath);
        assertThat(result).isEqualTo(validPath);
    }

    @Test
    void validWindowsAbsolutePathIsAccepted() throws Exception {
        String validPath = "C:\\firebase\\service-account.json";
        String result = invokeValidateAndSanitizePath(validPath);
        assertThat(result).isEqualTo(validPath);
    }

    @Test
    void pathWithWhitespaceIsTrimmed() throws Exception {
        String pathWithSpaces = "  /opt/firebase/service-account.json  ";
        String result = invokeValidateAndSanitizePath(pathWithSpaces);
        assertThat(result).isEqualTo("/opt/firebase/service-account.json");
    }

    // Helper method to invoke private validateAndSanitizePath method via reflection
    private String invokeValidateAndSanitizePath(String path) throws Exception {
        Method method = FirebaseAdminConfiguration.class.getDeclaredMethod("validateAndSanitizePath", String.class);
        method.setAccessible(true);
        return (String) method.invoke(firebaseAdminConfiguration, path);
    }
}
