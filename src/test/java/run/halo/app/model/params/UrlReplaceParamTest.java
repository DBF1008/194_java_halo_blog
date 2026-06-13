package run.halo.app.model.params;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import run.halo.app.model.enums.ReplaceableModule;

/**
 * UrlReplaceParam test.
 *
 * @author halo-dev
 */
class UrlReplaceParamTest {

    @Test
    void dryRunShouldDefaultToTrue() {
        UrlReplaceParam param = new UrlReplaceParam();
        assertEquals(Boolean.TRUE, param.getDryRun());
    }

    @Test
    void modulesShouldDefaultToEmptySet() {
        UrlReplaceParam param = new UrlReplaceParam();
        assertNotNull(param.getModules());
        assertTrue(param.getModules().isEmpty());
    }

    @Test
    void shouldAcceptModuleSet() {
        UrlReplaceParam param = new UrlReplaceParam();
        param.setModules(EnumSet.of(
            ReplaceableModule.POSTS, ReplaceableModule.ATTACHMENTS));
        assertEquals(2, param.getModules().size());
    }

    @Test
    void shouldAcceptOldAndNewUrl() {
        UrlReplaceParam param = new UrlReplaceParam();
        param.setOldUrl("http://old.example.com");
        param.setNewUrl("http://new.example.com");
        assertEquals("http://old.example.com", param.getOldUrl());
        assertEquals("http://new.example.com", param.getNewUrl());
    }
}
