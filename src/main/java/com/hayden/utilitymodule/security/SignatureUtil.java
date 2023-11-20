package com.hayden.utilitymodule.security;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.bson.ByteBuf;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@UtilityClass
@Slf4j
public class SignatureUtil {

    public static Optional<byte[]> TakeMessageDigest(byte[] input) {
        return TakeMessageDigest(input, null);
    }

    public static Optional<byte[]> TakeMessageDigest(byte[] input, @Nullable String algorithm) {
        Set<String> messageDigestAlgorithms = Security.getAlgorithms(MessageDigest.class.getSimpleName());
        return messageDigestAlgorithms
                .stream().filter(a -> a.equals(algorithm))
                .findAny()
                .or(() -> messageDigestAlgorithms.stream().filter(s -> s.equals("MD5"))
                        .findAny()
                )
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

}
