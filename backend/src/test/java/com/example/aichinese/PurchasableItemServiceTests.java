package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.service.CommerceMoneyService;
import com.example.aichinese.service.CommerceSettingsService;
import com.example.aichinese.service.PurchasableItemService;
import com.example.aichinese.service.model.ResolvedPurchasableItem;

class PurchasableItemServiceTests {

    private static final String ITEM_TYPE_LESSON = "LESSON";
    private static final long LESSON_ID = 10L;
    private static final BigDecimal HISTORICAL_PRICE = new BigDecimal("25.00");
    private static final String DEFAULT_CURRENCY = "MYR";

    @Test
    void freeAccessTypeUsesZeroCheckoutPriceWhenAHistoricalPriceIsPresent() {
        LessonRepository lessonRepository = mock(LessonRepository.class);
        CommerceMoneyService commerceMoneyService = mock(CommerceMoneyService.class);
        CommerceSettingsService commerceSettingsService = mock(CommerceSettingsService.class);
        PurchasableItemService service = purchasableItemService(
                lessonRepository,
                commerceMoneyService,
                commerceSettingsService);

        when(lessonRepository.findByIdAndContentStatus(LESSON_ID, ContentStatus.PUBLISHED))
                .thenReturn(Optional.of(freeLessonWithHistoricalPrice()));
        when(commerceMoneyService.normalize(BigDecimal.ZERO)).thenReturn(BigDecimal.ZERO);
        when(commerceSettingsService.getDefaultCurrency()).thenReturn(DEFAULT_CURRENCY);

        ResolvedPurchasableItem resolved = service.resolve(ITEM_TYPE_LESSON, LESSON_ID, null);

        assertThat(resolved.freeItem()).isTrue();
        assertThat(resolved.unitPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static PurchasableItemService purchasableItemService(
            LessonRepository lessonRepository,
            CommerceMoneyService commerceMoneyService,
            CommerceSettingsService commerceSettingsService) {
        return new PurchasableItemService(
                lessonRepository,
                mock(LearningModuleRepository.class),
                mock(CoursePackageRepository.class),
                commerceMoneyService,
                commerceSettingsService);
    }

    private static Lesson freeLessonWithHistoricalPrice() {
        Lesson lesson = new Lesson();
        lesson.setId(LESSON_ID);
        lesson.setTitle("Free lesson");
        lesson.setAccessType(AccessType.FREE);
        lesson.setPrice(HISTORICAL_PRICE);
        return lesson;
    }
}
