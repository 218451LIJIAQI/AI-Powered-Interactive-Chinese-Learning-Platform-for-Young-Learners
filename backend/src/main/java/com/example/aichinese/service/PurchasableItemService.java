package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PurchasableItemType;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.service.model.ResolvedPurchasableItem;

@Service
public class PurchasableItemService {

    private static final Logger log = LoggerFactory.getLogger(PurchasableItemService.class);

    private final LessonRepository lessonRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final CommerceMoneyService commerceMoneyService;
    private final CommerceSettingsService commerceSettingsService;

    public PurchasableItemService(
            LessonRepository lessonRepository,
            LearningModuleRepository learningModuleRepository,
            CoursePackageRepository coursePackageRepository,
            CommerceMoneyService commerceMoneyService,
            CommerceSettingsService commerceSettingsService) {
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository");
        this.learningModuleRepository = Objects.requireNonNull(learningModuleRepository, "learningModuleRepository");
        this.coursePackageRepository = Objects.requireNonNull(coursePackageRepository, "coursePackageRepository");
        this.commerceMoneyService = Objects.requireNonNull(commerceMoneyService, "commerceMoneyService");
        this.commerceSettingsService = Objects.requireNonNull(commerceSettingsService, "commerceSettingsService");
    }

    @Transactional(readOnly = true, noRollbackFor = ResponseStatusException.class)
    public ResolvedPurchasableItem resolve(String itemTypeValue, Long itemId, String featureKey) {
        PurchasableItemType itemType = PurchasableItemType.fromValue(itemTypeValue);
        log.debug("Resolving purchasable item: type={}, itemId={}", itemType, itemId);
        return switch (itemType) {
            case LESSON -> resolveLesson(itemId);
            case MODULE -> resolveModule(itemId);
            case PACKAGE -> resolvePackage(itemId);
            case PREMIUM_AI -> throw new ResponseStatusException(BAD_REQUEST,
                    "Premium AI purchases are not available yet.");
            case COURSE -> throw new ResponseStatusException(BAD_REQUEST,
                    "Course purchases are reserved for a future-compatible workflow.");
        };
    }

    private ResolvedPurchasableItem resolveLesson(Long itemId) {
        Lesson lesson = lessonRepository.findByIdAndContentStatus(itemId, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published lesson was not found."));
        validatePaidContent(lesson.getAccessType(), lesson.getPrice());
        return new ResolvedPurchasableItem(
                PurchasableItemType.LESSON,
                lesson.getId(),
                lesson.getTitle(),
                commerceMoneyService.normalize(defaultPrice(lesson.getPrice())),
                commerceSettingsService.getDefaultCurrency(),
                lesson.getTeacher(),
                isFree(lesson.getPrice()),
                null);
    }

    private ResolvedPurchasableItem resolveModule(Long itemId) {
        LearningModule module = learningModuleRepository.findByIdAndContentStatus(itemId, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published module was not found."));
        validatePaidContent(module.getAccessType(), module.getPrice());
        return new ResolvedPurchasableItem(
                PurchasableItemType.MODULE,
                module.getId(),
                module.getTitle(),
                commerceMoneyService.normalize(defaultPrice(module.getPrice())),
                commerceSettingsService.getDefaultCurrency(),
                module.getTeacher(),
                isFree(module.getPrice()),
                null);
    }

    private ResolvedPurchasableItem resolvePackage(Long itemId) {
        CoursePackage coursePackage = coursePackageRepository.findByIdAndContentStatus(itemId, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Published package was not found."));
        validatePaidContent(coursePackage.getAccessType(), coursePackage.getPrice());
        return new ResolvedPurchasableItem(
                PurchasableItemType.PACKAGE,
                coursePackage.getId(),
                coursePackage.getTitle(),
                commerceMoneyService.normalize(defaultPrice(coursePackage.getPrice())),
                commerceSettingsService.getDefaultCurrency(),
                coursePackage.getTeacher(),
                isFree(coursePackage.getPrice()),
                null);
    }

    private static void validatePaidContent(AccessType accessType, BigDecimal price) {
        if (accessType == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Purchasable content is missing its access type.");
        }

        if (accessType == AccessType.FREE) {
            return;
        }

        if (price == null || price.signum() <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Purchasable content has an invalid price.");
        }
    }

    private static BigDecimal defaultPrice(BigDecimal price) {
        return price == null ? BigDecimal.ZERO : price;
    }

    private static boolean isFree(BigDecimal price) {
        return defaultPrice(price).compareTo(BigDecimal.ZERO) <= 0;
    }
}
