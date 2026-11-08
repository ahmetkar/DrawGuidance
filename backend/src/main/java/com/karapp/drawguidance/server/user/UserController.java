package com.karapp.drawguidance.server.user;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kullanıcı kaydı ({@code /api/v1/users/me}). Kullanıcı Firebase ID jetonundan ({@code sub}) belirlenir; jetonu
 * {@code SecurityConfig} doğrular.
 *
 * <p>v40 {@code DELETE /me}: hesap ve kullanıcıya bağlı bütün veriler silinir, abonelik kayıtları kullanıcıdan
 * koparılarak saklanır ({@link UserService#delete}). Web sitesi de tarayıcıdan çağırır (CORS: {@code WebConfig}).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @GetMapping("/me")
    public User.Response me(@AuthenticationPrincipal Jwt jwt) {
        return users.get(jwt.getSubject()).toResponse();
    }

    @PutMapping("/me")
    public User.Response put(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UserRequest body) {
        // v40 e-posta ve doğrulanmışlığı jetondan (gövdedeki değerlere güvenilmez; bkz. UserService.TokenEmail)
        return users.upsert(jwt.getSubject(), body, UserService.TokenEmail.of(jwt)).toResponse();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        // v40 jetondaki doğrulanmış e-posta: aynı e-postanın bekleyen web silme talepleri kapatılır
        users.delete(jwt.getSubject(), UserService.TokenEmail.of(jwt).verifiedEmail());
        return ResponseEntity.noContent().build();
    }
}
