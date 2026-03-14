package com.example.chatinterface;

import java.lang.reflect.Field;

/**
 * Utilitaire de test pour manipuler les entites JPA dont l'ID est gere
 * par {@code @GeneratedValue} (pas de setter public).
 * <p>
 * Usage : {@code TestEntityHelper.setId(entity, 42L);}
 */
public final class TestEntityHelper {

    private TestEntityHelper() {}

    /**
     * Affecte un ID a une entite JPA via reflection.
     * Remonte la hierarchie de classes pour trouver le champ {@code id}.
     */
    public static void setId(Object entity, Long id) {
        try {
            Field idField = findField(entity.getClass(), "id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                    "Impossible de setter l'id sur " + entity.getClass().getSimpleName()
                    + ". Verifiez que le champ 'id' existe.", e);
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + clazz.getName());
    }
}
