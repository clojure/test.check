package clojure.test.check;
/**
 * Adapted from https://github.com/nahi/siphash-java-inline/blob/af061ca86ef70c08e4e6b3a03dc8328e06fe7c5b/src/main/java/org/jruby/util/SipHashInline.java
 */
public class SipHashish {

  /*
   *  A slight variation on SipHash-24 specialized for 64 bits of input.
   *
   *  The main difference is that we don't append the length of
   *  the input.
   */
    public static long hashish(long k0, long k1, long data) {
        long v0 = 0x736f6d6570736575L ^ k0;
        long v1 = 0x646f72616e646f6dL ^ k1;
        long v2 = 0x6c7967656e657261L ^ k0;
        long v3 = 0x7465646279746573L ^ k1;
        long m;
        int i = 0;

        m = data;
        // MSGROUND {
            v3 ^= m;
            // SIPROUND {
                v0 += v1;                    v2 += v3;
                v1 = (v1 << 13) | v1 >>> 51; v3 = (v3 << 16) | v3 >>> 48;
                v1 ^= v0;                    v3 ^= v2;
                v0 = (v0 << 32) | v0 >>> 32; v2 += v1;
                v0 += v3;                    v1 = (v1 << 17) | v1 >>> 47;
                v3 = (v3 << 21) | v3 >>> 43; v1 ^= v2;
                v3 ^= v0;                    v2 = (v2 << 32) | v2 >>> 32;
            // }
            // SIPROUND {
                v0 += v1;                    v2 += v3;
                v1 = (v1 << 13) | v1 >>> 51; v3 = (v3 << 16) | v3 >>> 48;
                v1 ^= v0;                    v3 ^= v2;
                v0 = (v0 << 32) | v0 >>> 32; v2 += v1;
                v0 += v3;                    v1 = (v1 << 17) | v1 >>> 47;
                v3 = (v3 << 21) | v3 >>> 43; v1 ^= v2;
                v3 ^= v0;                    v2 = (v2 << 32) | v2 >>> 32;
            // }
            v0 ^= m;
        // }

        // finishing...
        v2 ^= 0xff;
        // SIPROUND {
            v0 += v1;                    v2 += v3;
            v1 = (v1 << 13) | v1 >>> 51; v3 = (v3 << 16) | v3 >>> 48;
            v1 ^= v0;                    v3 ^= v2;
            v0 = (v0 << 32) | v0 >>> 32; v2 += v1;
            v0 += v3;                    v1 = (v1 << 17) | v1 >>> 47;
            v3 = (v3 << 21) | v3 >>> 43; v1 ^= v2;
            v3 ^= v0;                    v2 = (v2 << 32) | v2 >>> 32;
        // }
        // SIPROUND {
            v0 += v1;                    v2 += v3;
            v1 = (v1 << 13) | v1 >>> 51; v3 = (v3 << 16) | v3 >>> 48;
            v1 ^= v0;                    v3 ^= v2;
            v0 = (v0 << 32) | v0 >>> 32; v2 += v1;
            v0 += v3;                    v1 = (v1 << 17) | v1 >>> 47;
            v3 = (v3 << 21) | v3 >>> 43; v1 ^= v2;
            v3 ^= v0;                    v2 = (v2 << 32) | v2 >>> 32;
        // }
        // SIPROUND {
            v0 += v1;                    v2 += v3;
            v1 = (v1 << 13) | v1 >>> 51; v3 = (v3 << 16) | v3 >>> 48;
            v1 ^= v0;                    v3 ^= v2;
            v0 = (v0 << 32) | v0 >>> 32; v2 += v1;
            v0 += v3;                    v1 = (v1 << 17) | v1 >>> 47;
            v3 = (v3 << 21) | v3 >>> 43; v1 ^= v2;
            v3 ^= v0;                    v2 = (v2 << 32) | v2 >>> 32;
        // }
        // SIPROUND {
            v0 += v1;                    v2 += v3;
            v1 = (v1 << 13) | v1 >>> 51; v3 = (v3 << 16) | v3 >>> 48;
            v1 ^= v0;                    v3 ^= v2;
            v0 = (v0 << 32) | v0 >>> 32; v2 += v1;
            v0 += v3;                    v1 = (v1 << 17) | v1 >>> 47;
            v3 = (v3 << 21) | v3 >>> 43; v1 ^= v2;
            v3 ^= v0;                    v2 = (v2 << 32) | v2 >>> 32;
        // }
        return v0 ^ v1 ^ v2 ^ v3;
    }
}
