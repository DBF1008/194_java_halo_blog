package run.halo.app.listener.freemarker;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import freemarker.template.Configuration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import run.halo.app.cache.AbstractStringCacheStore;
import run.halo.app.event.options.OptionUpdatedEvent;
import run.halo.app.model.support.HaloConst;
import run.halo.app.service.OptionService;
import run.halo.app.service.ThemeService;
import run.halo.app.service.ThemeSettingService;
import run.halo.app.service.UserService;

/**
 * Freemarker config aware listener test.
 *
 * <p>Verifies that an option updated event refreshes the options cache, which is the second half of
 * the snapshot apply -&gt; save -&gt; event -&gt; cache refresh chain.
 *
 * @author halo
 */
class FreemarkerConfigAwareListenerTest {

    @Mock
    OptionService optionService;

    @Mock
    Configuration configuration;

    @Mock
    ThemeService themeService;

    @Mock
    ThemeSettingService themeSettingService;

    @Mock
    UserService userService;

    @Mock
    AbstractStringCacheStore cacheStore;

    FreemarkerConfigAwareListener listener;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        given(optionService.isEnabledAbsolutePath()).willReturn(false);
        given(themeService.fetchActivatedTheme()).willReturn(Optional.empty());
        listener = new FreemarkerConfigAwareListener(optionService, configuration, themeService,
            themeSettingService, userService, cacheStore);
    }

    @Test
    void onOptionUpdateEvictsOptionsCacheAndFlushes() throws Exception {
        listener.onOptionUpdate(new OptionUpdatedEvent(this));

        then(optionService).should().flush();
        then(cacheStore).should().delete(HaloConst.OPTIONS_CACHE_KEY);
    }
}
