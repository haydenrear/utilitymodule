package com.hayden.utilitymodule.security;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

@UtilityClass
@Slf4j
public class SignatureUtil {

    public static Optional<byte[]> TakeMessageDigest(byte[] input) {
        return TakeMessageDigest(input, null);
    }

    public static Optional<byte[]> TakeMessageDigest(Path input, @Nullable String algorithm) {
        return retrieveDigestAlgorithm(algorithm).flatMap(s -> {
            try {
                return Optional.of(MessageDigest.getInstance(s));
            } catch (NoSuchAlgorithmException e) {
                log.error("Could not retrieve message digest instance: {}", e.getMessage());
            }
            return Optional.empty();
        }).flatMap(md -> {
            try(InputStream is = Files.newInputStream(input); DigestInputStream ds = new DigestInputStream(is, md)) {
                return Optional.of(ds.getMessageDigest().digest());
            } catch (IOException e) {
                log.error("Could not create digest input stream: {}.", e.getMessage());
            }
            return Optional.empty();
        });
    }

    public static @NotNull String hashToString(String toHash, MessageDigest digest) {
        return hashToString(toHash.getBytes(StandardCharsets.UTF_8), digest);
    }

    public static @NotNull String hashToString(String toHash, MessageDigest digest, Charset charsets) {
        return hashToString(toHash.getBytes(charsets), digest);
    }

    public static @NotNull String hashToString(byte[] toHash, MessageDigest digest) {
        byte[] dig = digest.digest(toHash);
        return Base64.getEncoder().encodeToString(dig);
    }

    public static Optional<byte[]> TakeMessageDigest(byte[] input, @Nullable String algorithm) {
        Optional<String> retrieveDigestAlgorithm = retrieveDigestAlgorithm(algorithm);
        return retrieveDigestAlgorithm
                .flatMap(md5Algorithm -> {
                    try {
                        byte[] digest = MessageDigest.getInstance(md5Algorithm).digest(input);
                        return Optional.of(digest);
                    } catch (NoSuchAlgorithmException e) {
                        log.error("Error attempting to get {}: {}.", md5Algorithm, e.getMessage());
                    }
                    return Optional.empty();
                });
    }

    @NotNull
    private static Optional<String> retrieveDigestAlgorithm(@Nullable String algorithm) {
        Set<String> messageDigestAlgorithms = Security.getAlgorithms(MessageDigest.class.getSimpleName());
        Optional<String> retrieveDigestAlgorithm = messageDigestAlgorithms
                .stream().filter(a -> a.equals(algorithm))
                .findAny()
                .or(() -> messageDigestAlgorithms.stream().filter(s -> s.equals("MD5"))
                        .findAny()
                );
        return retrieveDigestAlgorithm;
    }

}
