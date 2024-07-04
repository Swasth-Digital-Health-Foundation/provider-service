package org.swasth.hcx.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

@Component
public class AESEncryption {

    private static final Logger logger = LoggerFactory.getLogger(AESEncryption.class);

    private  SecretKeySpec secretKey;
    private String algo;

    public AESEncryption(@Value("${cr.crypto.algo}") String algo,
                         @Value("${cr.crypto.secret}") String secret,
                         @Value("${cr.crypto.type}") String encryptionType) {
        this.algo = algo;
        this.secretKey = prepareSecretKey(algo, secret, encryptionType);
    }


    public SecretKeySpec prepareSecretKey(String algo, String myKey, String encryptionType) {
        try {
            byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance(encryptionType);
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            return new SecretKeySpec(key, algo);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            logger.error("NoSuchAlgorithmException :: Problem while encryption ", e);
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String strToEncrypt) {
        try {
            if (null != strToEncrypt && !"".equals(strToEncrypt.trim())) {
                Cipher cipher = Cipher.getInstance(algo);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
            } else {
                return strToEncrypt;
            }
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            logger.error("AESEncryption::encrypt :: Problem while encryption ", e);
        }
        return null;
    }

    public String decrypt(String strToDecrypt) {
        try {
            if (null != strToDecrypt && !"".equals(strToDecrypt.trim())) {
                Cipher cipher = Cipher.getInstance(algo);
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
            } else {
                return strToDecrypt;
            }

        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            logger.error("AESEncryption::decrypt :: Problem while decryption ", e);
        }
        return null;
    }
}
