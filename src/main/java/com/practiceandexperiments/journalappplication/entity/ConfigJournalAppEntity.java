package com.practiceandexperiments.journalappplication.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "config_journal_app")
@NoArgsConstructor
@Data
public class ConfigJournalAppEntity {
    private String key;
    private String value;
}
