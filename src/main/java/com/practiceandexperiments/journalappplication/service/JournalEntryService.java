package com.practiceandexperiments.journalappplication.service;

import com.practiceandexperiments.journalappplication.entity.JournalEntry;
import com.practiceandexperiments.journalappplication.entity.User;
import com.practiceandexperiments.journalappplication.repository.JournalEntryRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class JournalEntryService {

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private UserService userService;

    @Transactional
    public void saveEntry(JournalEntry journalEntry, String username) {
        try{
            User user = userService.findByUserName(username);
            journalEntry.setDate(LocalDateTime.now());
            JournalEntry saved = journalEntryRepository.save(journalEntry);
            user.getJournalEntries().add(saved);
            // below line is for checking whether the transactional control is working or not
//            user.setUserName(null);
            userService.saveUser(user);
        } catch (Exception e) {
            log.error("Error saving journal entry: {}", e.getMessage());
            throw new RuntimeException("Error saving journal entry : " + e.getMessage());
        }
    }

    public void saveEntry(JournalEntry journalEntry) {
        try{
            journalEntryRepository.save(journalEntry);
        } catch (Exception e) {
            log.error("Error saving journal entry: {}", e.getMessage());
        }
    }

    public List<JournalEntry> getAll() {
        return journalEntryRepository.findAll();
    }

    public Optional<JournalEntry> findById(ObjectId id){
        return journalEntryRepository.findById(id);
    }

    @Transactional
    public boolean deleteById(ObjectId id, String username){
        boolean removed = false;
        try {
            User user = userService.findByUserName(username);
            removed = user.getJournalEntries().removeIf(x -> x.getId().equals(id));
            if(removed){
                userService.saveUser(user);
                journalEntryRepository.deleteById(id);
                log.info("Journal entry deleted successfully : "+id);
            }
        } catch (Exception e) {
            log.error("Error deleting journal entry: {}", e.getMessage());
            throw new RuntimeException("Error deleting journal entry : " + e.getMessage());
        }
        return removed;
    }

//    public List<JournalEntry> findByUsername(String username){
//    }

}
