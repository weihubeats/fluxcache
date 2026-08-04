package com.fluxcache.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 空值占位符：单例、equals 语义、hashCode。
 *
 * @author : wh
 * @date : 2026/8/4
 */
public class FluxNullValueTest {

    @Test
    public void singleton_toString() {
        assertEquals("null", FluxNullValue.INSTANCE.toString());
    }

    @Test
    public void equals_sameInstance_orNull() {
        assertTrue(FluxNullValue.INSTANCE.equals(FluxNullValue.INSTANCE));
        assertTrue(FluxNullValue.INSTANCE.equals(null));
    }

    @Test
    public void equals_otherInstance_false() {
        assertFalse(FluxNullValue.INSTANCE.equals(new FluxNullValue()));
    }

    @Test
    public void hashCode_constant() {
        assertEquals(FluxNullValue.class.hashCode(), FluxNullValue.INSTANCE.hashCode());
    }
}
