package com.wing.backendapiexpensespringboot.service;

import com.wing.backendapiexpensespringboot.config.AppConfig;
import com.wing.backendapiexpensespringboot.repository.CategorySeedRepository;
import com.wing.backendapiexpensespringboot.repository.CategorySeedRepository.DefaultCategorySeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultCategoryProvisioningService {

    private static final List<DefaultCategorySeed> DEFAULT_CATEGORIES = List.of(
            new DefaultCategorySeed("Food", "food", "#FF6B6B", "EXPENSE", 10),
            new DefaultCategorySeed("Transport", "car", "#4ECDC4", "EXPENSE", 20),
            new DefaultCategorySeed("Bills", "zap", "#FFD93D", "EXPENSE", 30),
            new DefaultCategorySeed("Shopping", "shopping", "#95E1D3", "EXPENSE", 40),
            new DefaultCategorySeed("Entertainment", "game", "#A78BFA", "EXPENSE", 50),
            new DefaultCategorySeed("Health", "heart", "#F87171", "EXPENSE", 60),
            new DefaultCategorySeed("Housing", "home", "#38BDF8", "EXPENSE", 70),
            new DefaultCategorySeed("Subscription", "sparkles", "#FF4D4D", "EXPENSE", 80),
            new DefaultCategorySeed("Childcare", "baby", "#FFB6C1", "EXPENSE", 90),
            new DefaultCategorySeed("Pets", "pet", "#A52A2A", "EXPENSE", 100),
            new DefaultCategorySeed("Maintenance", "briefcase", "#fbaf24", "EXPENSE", 110),
            new DefaultCategorySeed("Electronics", "laptop", "#0000FF", "EXPENSE", 120),
            new DefaultCategorySeed("Clothing", "shopping", "#800080", "EXPENSE", 130),
            new DefaultCategorySeed("Fees", "wallet", "#FF0000", "EXPENSE", 140),
            new DefaultCategorySeed("Tax", "wallet", "#FFA500", "EXPENSE", 150),
            new DefaultCategorySeed("Donation", "gift", "#008000", "EXPENSE", 160),
            new DefaultCategorySeed("Other", "tag", "#9CA3AF", "EXPENSE", 170),
            new DefaultCategorySeed("Salary", "wallet", "#22C55E", "INCOME", 180),
            new DefaultCategorySeed("Freelance", "briefcase", "#14B8A6", "INCOME", 190),
            new DefaultCategorySeed("Investment", "piggybank", "#10B981", "INCOME", 200),
            new DefaultCategorySeed("Gift Income", "gift", "#34D399", "INCOME", 210),
            new DefaultCategorySeed("Refund", "sparkles", "#2DD4BF", "INCOME", 220));

    private final CategorySeedRepository categorySeedRepository;
    private final DatabaseRetryExecutor databaseRetryExecutor;
    private final AppConfig appConfig;

    public int provisionMissingDefaultCategories(String firebaseUid) {
        if (!appConfig.getBootstrap().isDefaultCategoriesEnabled() || !StringUtils.hasText(firebaseUid)) {
            return 0;
        }

        int insertedRows = databaseRetryExecutor.execute(
                "default category seed",
                () -> categorySeedRepository.insertMissingDefaultCategories(firebaseUid, DEFAULT_CATEGORIES));
        log.debug("Provisioned {} default category rows for user {}", insertedRows, firebaseUid);
        return insertedRows;
    }
}
