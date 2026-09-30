package io.picstr.app.config;

import java.util.List;
import java.util.function.Supplier;

import io.picstr.app.model.ThumbnailKeys;
import io.picstr.app.repository.PhotoRepository;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Assets are public so they can be embedded and linked (e.g. from the RSS feed), except the files of
 * archived photos: those require a logged-in user, like the archive views that show them.
 */
@Component
public class ArchivedAssetAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final PhotoRepository photoRepository;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public ArchivedAssetAuthorizationManager(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        var key = context.getVariables().get("key");
        if (key == null || !isArchived(key)) {
            return new AuthorizationDecision(true);
        }
        return new AuthorizationDecision(trustResolver.isAuthenticated(authentication.get()));
    }

    private boolean isArchived(String key) {
        var originals = ThumbnailKeys.isThumbnail(key) ? ThumbnailKeys.originalCandidates(key) : List.of(key);
        return photoRepository.existsByInternalFilenameInAndDeleteDateIsNotNull(originals);
    }
}
