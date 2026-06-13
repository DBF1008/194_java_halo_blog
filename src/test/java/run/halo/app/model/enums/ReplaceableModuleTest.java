package run.halo.app.model.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * ReplaceableModule enum test.
 *
 * @author halo-dev
 */
class ReplaceableModuleTest {

    @Test
    void shouldHaveNineModules() {
        assertEquals(9, ReplaceableModule.values().length);
    }

    @Test
    void eachModuleShouldHaveDisplayName() {
        for (ReplaceableModule module : ReplaceableModule.values()) {
            assertNotNull(module.getDisplayName(),
                module.name() + " should have a display name");
            assertTrue(module.getDisplayName().length() > 0,
                module.name() + " display name should not be empty");
        }
    }

    @Test
    void eachModuleShouldHaveFields() {
        for (ReplaceableModule module : ReplaceableModule.values()) {
            assertNotNull(module.getFields(),
                module.name() + " should have fields");
            assertTrue(module.getFields().length > 0,
                module.name() + " should have at least one field");
        }
    }

    @Test
    void postsShouldHaveThreeFields() {
        assertEquals(3, ReplaceableModule.POSTS.getFields().length);
    }

    @Test
    void commentsShouldHaveOneField() {
        assertEquals(1, ReplaceableModule.POST_COMMENTS.getFields().length);
        assertEquals(1, ReplaceableModule.SHEET_COMMENTS.getFields().length);
        assertEquals(1, ReplaceableModule.JOURNAL_COMMENTS.getFields().length);
    }

    @Test
    void attachmentsShouldHaveTwoFields() {
        assertEquals(2, ReplaceableModule.ATTACHMENTS.getFields().length);
    }

    @Test
    void photosShouldHaveTwoFields() {
        assertEquals(2, ReplaceableModule.PHOTOS.getFields().length);
    }
}
