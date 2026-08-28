package org.t246osslab.easybuggy4sb.performance;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.t246osslab.easybuggy4sb.controller.AbstractController;

@Controller
public class SlowRegularExpressionController extends AbstractController {

    // 対策1: バックトラッキングが発生しない正規表現に修正
    // 修正前: ^([a-z0-9]+[-]{0,1}){1,100}$ （2重の可変長繰り返しによりReDoSが発生）
    // 修正後: ハイフンの前後を明示的に分離し、バックトラッキングを線形に抑制
    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+){0,99}$");

    // 対策2: サーバ側の入力長上限（HTMLのmaxlength="50"はクライアント側のみで回避可能）
    private static final int MAX_WORD_LENGTH = 50;

    @RequestMapping(value = "/slowre")
    public ModelAndView process(@RequestParam(value = "word", required = false) String word, ModelAndView mav,
            Locale locale) {
        String message;
        setViewAndCommonObjects(mav, locale, "slowregex");
        if (!StringUtils.isBlank(word)) {
            // 対策2: サーバ側入力長バリデーション
            if (word.length() > MAX_WORD_LENGTH) {
                message = msg.getMessage("msg.not.match.regular.expression", null, locale);
            } else if (isMatched(word)) {
                message = msg.getMessage("msg.match.regular.expression", null, locale);
            } else {
                message = msg.getMessage("msg.not.match.regular.expression", null, locale);
            }
        } else {
            message = msg.getMessage("msg.enter.string", null, locale);
        }
        mav.addObject("msg", message);
        return mav;
    }

    private boolean isMatched(String word) {
        log.info("Start Date: {}", new Date());
        // 対策3: タイムアウト制御（万が一の多層防御）
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() -> PATTERN.matcher(word).matches());
        try {
            boolean result = future.get(3, TimeUnit.SECONDS);
            log.info("End Date: {}", new Date());
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Regular expression matching timed out for input length: {}", word.length());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Regular expression matching was interrupted");
            return false;
        } catch (ExecutionException e) {
            log.error("Regular expression matching failed", e.getCause());
            return false;
        } finally {
            executor.shutdownNow();
        }
    }
}
