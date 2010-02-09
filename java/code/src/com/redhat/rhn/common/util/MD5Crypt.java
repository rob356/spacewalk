/**
 * Copyright (c) 2009--2010 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.common.util;

import org.apache.commons.codec.binary.Hex;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * MD5Crypt
 * Utility class to create/check passwords generated by the perl crypt
 * function. Passwords are in the format of $1$salt$encodedpassword.
 * @version $Rev$
 */
public class MD5Crypt {
    
    /**
     * prefix is the prefix to use for our encoded string.
     * b64t is a string containing acceptable salt chars.
     */
    private static String prefix = "$1$";
    private static String b64t = 
                "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    
    /**
     * Private Constructor 
     */
    private MD5Crypt() { 
    }
    
    /**
     * getSalt - Cleans salt parameter
     * @param prefix - prefix for salt ($1$)
     * @param salt - string in question
     * @return Returns the salt portion of passed-in salt
     */
    private static String getSalt(String salt) {
        /**
         * - If salt starts with $1$ (prefix) then discard that portion of it.
         * - If we recieve a string such as $1$salt$something else, we only want
         *   to keep the salt portion of it.
         * - Ensure salt length is <= 8.
         */
        if (salt.startsWith(prefix)) {
            salt = salt.substring(prefix.length());
        }
        
        int end = salt.indexOf("$");
        if (end != -1) {
            salt = salt.substring(0, end);
        }
        
        if (salt.length() > 8) {
            salt = salt.substring(0, 8);
        }
        
        return salt;
    }
    
    /**
     * to64 - Utility function for generateEncodedKey
     * @param value value
     * @param length length
     * @return String
     */
    private static String to64(int value, int length) {
        StringBuffer out = new StringBuffer();
        
        while (length > 0) {
            out.append(b64t.substring((value & 0x3f), (value & 0x3f) + 1));
            --length;
            value >>= 6;
        }
        return out.toString();
    }
    
    /**
     * generateEncodedKey - Handles generating the encoded key from the
     * final digest.
     * @param digest - Digest to use for encoding
     * @param salt - salt to prepend to output
     * @return Returns encoded string $1$salt$encodedkey
     */
    private static String generateEncodedKey(byte[] digest, String salt) {

        StringBuffer out = new StringBuffer(prefix);
        out.append(salt);
        out.append("$");
        
        int val = ((digest[0] & 0xff) << 16) |
                  ((digest[6] & 0xff) << 8) |
                  (digest[12] & 0xff);
        out.append(to64(val, 4));

        val = ((digest[1] & 0xff) << 16) |
              ((digest[7] & 0xff) << 8) |
              (digest[13] & 0xff);
        out.append(to64(val, 4));
        
        val = ((digest[2] & 0xff) << 16) |
              ((digest[8] & 0xff) << 8) |
              (digest[14] & 0xff);
        out.append(to64(val, 4));
        
        val = ((digest[3] & 0xff) << 16) |
              ((digest[9] & 0xff) << 8) |
              (digest[15] & 0xff);
        out.append(to64(val, 4));
        
        val = ((digest[4] & 0xff) << 16) |
              ((digest[10] & 0xff) << 8) |
              (digest[5] & 0xff);
        out.append(to64(val, 4));
        
        val = (digest[11] & 0xff);
        out.append(to64(val, 2));
        
        return out.toString();
    }
    
    /**
     * crypt
     * Method to help in setting passwords.
     * @param key - The key to encode
     * @return Returns a string in the form of "$1$RANDOMsalt$encodedkey"
     */
    public static String crypt(String key) {
        StringBuffer salt = new StringBuffer();
        //Generate random 8-char salt
        Random r = new Random();
        for (int i = 0; i < 8; i++) {
            int rand = Math.abs(r.nextInt()) % b64t.length();
            salt.append(b64t.charAt(rand));
        }       
        //Return encoded string: $1$salt$encodedkey
        return crypt(key, salt.toString());
    }
    
    /**
     * crypt
     * Encodes a key using a salt (s) in the same manner as the 
     * perl crypt() function.
     * This method will be called directly when checking passwords. It will
     * also be called from the crypt(key) function when setting a password.
     * @param key - The key to encode
     * @param s - The salt
     * @return Returns a string in the form of "$1$salt$encodedkey"
     * @throws MD5CryptException
     */
    public static String crypt(String key, String s) {
        
        /**
         * If this method is called in order for a comparison, s may be
         * in the form of $1$salt$encodedkey. We'll need to extract
         * the salt from it.
         */
        String salt = getSalt(s);

        MessageDigest md1;
        MessageDigest md2; 
        try {
            md1 = MessageDigest.getInstance("MD5");
            md2 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException e) {
            throw new MD5CryptException("Problem getting MD5 message digest " +
                                        "(NoSuchAlgorithm Exception).");
        }
        
        byte[] keyBytes = key.getBytes();
        byte[] saltBytes = salt.getBytes();
        byte[] prefixBytes = prefix.getBytes();
        int keylength = key.length();
        
        //Update first MessageDigest - key/prefix/salt
        md1.update(keyBytes);
        md1.update(prefixBytes);
        md1.update(saltBytes);
        
        //Update second MessageDigest - key/salt/key
        md2.update(keyBytes);
        md2.update(saltBytes);
        md2.update(keyBytes);
        
        
        byte[] md2Digest = md2.digest();
        int md2DigestLength = md2Digest.length;
        
        for (int i = keylength; i > 0; i -= md2DigestLength) {
            if (i > md2DigestLength) {
                md1.update(md2Digest, 0, md2DigestLength);
            }
            else {
                md1.update(md2Digest, 0, i);
            }
        }
        
        md2.reset();
        
        for (int i = keylength; i > 0; i >>= 1) {
            if ((i & 1) == 1) {
                md1.update((byte) 0);
            } 
            else {
                md1.update(keyBytes[0]);
            }
        }
        
        byte[] md1Digest = md1.digest();
        
        for (int i = 0; i < 1000; i++) {
            md2.reset();
            if ((i & 1) == 1) {
                md2.update(keyBytes);
            }
            else {
                md2.update(md1Digest);
            }
            if ((i % 3) != 0) {
                md2.update(saltBytes);
            }
            if ((i % 7) != 0) {
                md2.update(keyBytes);
            }
            if ((i & 1) != 0) {
                md2.update(md1Digest);
            }
            else {
                md2.update(keyBytes);
            }
            md1Digest = md2.digest();
        }
        
        return generateEncodedKey(md1Digest, salt);
    }
    
    /**
     * MD5 and Hexify a string.  Take the input string, MD5 encode it
     * and then turn it into Hex.
     * @param inputString you want md5hexed
     * @return md5hexed String.
     */
    public static String md5Hex(String inputString) {
        byte[] secretBytes;
        try {
            secretBytes = inputString.getBytes("UTF-8");
            
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UnsupportedEncodingException when" +
                    " trying to convert a String into UTF-8.  This shouldn't happen.", e);
        }
        return md5Hex(secretBytes);
    }
    
    /**
     * MD5 and Hexify an array of bytes.  Take the input array, MD5 encodes it
     * and then turns it into Hex.
     * @param secretBytes you want md5hexed
     * @return md5hexed String.
     */
    public static String md5Hex(byte[] secretBytes) {
        String retval = null;
        // add secret
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
            //byte[] secretBytes = inputString.getBytes("UTF-8");
            md.update(secretBytes);
            // generate the digest
            byte[] digest = md.digest();
            // hexify this puppy
            retval = new String(Hex.encodeHex(digest));
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("NoSuchAlgorithm: MD5.  Something" +
                    " weird with your JVM, you should be able to do this.", e);
        }
        return retval;
    }
}
