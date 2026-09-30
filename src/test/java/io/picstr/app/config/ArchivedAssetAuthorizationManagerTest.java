package io.picstr.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import io.picstr.app.repository.PhotoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@ExtendWith(MockitoExtension.class)
class ArchivedAssetAuthorizationManagerTest {

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    private static final Authentication USER = UsernamePasswordAuthenticationToken.authenticated(
            "user", null, List.of());

    @Mock
    private PhotoRepository photoRepository;

    @Test
    void activePhotoIsPublic() {
        when(photoRepository.existsByInternalFilenameInAndDeleteDateIsNotNull(List.of("a.jpg"))).thenReturn(false);

        assertThat(authorize(ANONYMOUS, "a.jpg")).isTrue();
    }

    @Test
    void archivedPhotoIsDeniedToAnonymousUsers() {
        when(photoRepository.existsByInternalFilenameInAndDeleteDateIsNotNull(List.of("a.jpg"))).thenReturn(true);

        assertThat(authorize(ANONYMOUS, "a.jpg")).isFalse();
    }

    @Test
    void archivedThumbnailIsDeniedToAnonymousUsers() {
        when(photoRepository.existsByInternalFilenameInAndDeleteDateIsNotNull(List.of("a.jpg", "a"))).thenReturn(true);

        assertThat(authorize(ANONYMOUS, "thumb_a.jpg")).isFalse();
        verify(photoRepository).existsByInternalFilenameInAndDeleteDateIsNotNull(List.of("a.jpg", "a"));
    }

    @Test
    void archivedPhotoIsAllowedForLoggedInUsers() {
        when(photoRepository.existsByInternalFilenameInAndDeleteDateIsNotNull(List.of("a.jpg", "a"))).thenReturn(true);

        assertThat(authorize(USER, "thumb_a.jpg")).isTrue();
    }

    @Test
    void archivedNonJpegThumbnailIsDeniedToAnonymousUsers() {
        when(photoRepository.existsByInternalFilenameInAndDeleteDateIsNotNull(List.of("a.png.jpg", "a.png")))
                .thenReturn(true);

        assertThat(authorize(ANONYMOUS, "thumb_a.png.jpg")).isFalse();
    }

    private boolean authorize(Authentication authentication, String key) {
        var manager = new ArchivedAssetAuthorizationManager(photoRepository);
        var context = new RequestAuthorizationContext(new MockHttpServletRequest(), Map.of("key", key));
        return manager.authorize(() -> authentication, context).isGranted();
    }
}
