package ru.skillfactorydemo.tgbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.skillfactorydemo.tgbot.dto.ValuteCursOnDate;
import ru.skillfactorydemo.tgbot.entity.ActiveChat;
import ru.skillfactorydemo.tgbot.repository.ActiveChatRepository;

import javax.xml.datatype.DatatypeConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final ActiveChatRepository activeChatRepository;
    private final BotService botService;
    private final CentralRussianBankService centralRussianBankService;
    private final List<ValuteCursOnDate> previousRates = new ArrayList<>();

    @Scheduled(cron = "0 0 0/3 ? * *")
    public void notifyAboutChangesInCurrencyRate() {
        try {
            List<ValuteCursOnDate> currentRates = centralRussianBankService.getCurrenciesFromCbr();
            Set<Long> activeChatIds = activeChatRepository.findAll().stream()
                    .map(ActiveChat::getChatId)
                    .collect(Collectors.toSet());
            if (!previousRates.isEmpty()) {
                for (int index = 0; index < currentRates.size(); index++) {
                    if (currentRates.get(index).getCourse() - previousRates.get(index).getCourse() >= 10.0) {
                        botService.sendNotificationToAllActiveChats(
                                "Курс " + currentRates.get(index).getName() +
                                        " увеличился на 10р", activeChatIds);
                    } else if (previousRates.get(index).getCourse() - currentRates.get(index).getCourse() >= 10.0) {
                        botService.sendNotificationToAllActiveChats(
                                "Курс " + currentRates.get(index).getName() +
                                        " уменьшился на 10р", activeChatIds);
                    }
                }
            } else {
                previousRates.addAll(currentRates);
            }
        } catch (DatatypeConfigurationException exc) {
            log.error("Возникла проблема при получении данных от сервисов ЦБ РФ", exc);
        }
    }
}
