package ru.skillfactorydemo.tgbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.skillfactorydemo.tgbot.dto.ValuteCursOnDate;
import ru.skillfactorydemo.tgbot.entity.ActiveChat;
import ru.skillfactorydemo.tgbot.repository.ActiveChatRepository;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class BotService extends TelegramLongPollingBot {

    private static final String CURRENT_RATES = "/currentrates";
    private static final String ADD_INCOME = "/addincome";
    private static final String ADD_SPEND = "/addspend";

    private final CentralRussianBankService centralRussianBankService;
    private final FinanceService financeService;
    private final ActiveChatRepository activeChatRepository;

    @Value("${bot.api.key}")
    private String apiKey;

    @Value("${bot.name}")
    private String name;

    private Map<Long, List<String>> previousCommands = new ConcurrentHashMap<>();

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();//Получаем сообщение от пользователя
        try {
            SendMessage response = new SendMessage();// реализация команды отправки сообщения, которую выполнит
            //подключенная библиотека
            Long chatId = message.getChatId(); // ID чата
            response.setChatId(String.valueOf(chatId)); // определяем в какой чат отвечать
            // сравниваем, что прислал пользователь, и какие команды мы можем обработать.
            // Пока что у нас только одна команда
            if (CURRENT_RATES.equalsIgnoreCase(message.getText())) {
                //Получаем все курсы валют на текущий момент и проходимся по ним в цикле
                for (ValuteCursOnDate valuteCursOnDate : centralRussianBankService.getCurrenciesFromCbr()) {
                    //В данной строчке мы собираем наше текстовое сообщение
                    //StringUtils.defaultBlank – это метод из библиотеки Apache Commons,
                    // который нам нужен для того, чтобы на первой итерации нашего цикла
                    // была вставлена пустая строка вместо null, а на следующих итерациях не перетерся текст,
                    // полученный из предыдущих итерации. Подключение библиотеки см. ниже
                    response.setText(StringUtils.defaultIfBlank(response.getText(), "") +
                            valuteCursOnDate.getName() + "-" + valuteCursOnDate.getCourse() + "\n");
                }
            } else if (ADD_INCOME.equalsIgnoreCase(message.getText())) {
                response.setText("Отправьте мне сумму полученного дохода");
            } else if (ADD_SPEND.equalsIgnoreCase(message.getText())) {
                response.setText("Отправьте мне сумму расходов");
            } else {
                response.setText(financeService.addFinanceOperation(getPreviousCommand(message.getChatId()),
                        message.getText(), message.getChatId()));
            }
            putPreviousCommand(message.getChatId(), message.getText());
            //Теперь мы сообщаем, что пора бы и ответ отправлять
            execute(response);
            //Проверяем, есть ли у нас такой chatId в базе, если нет, то добавляем,
            // если есть, то пропускаем данный шаг
            if (activeChatRepository.findActiveChatByChatId(chatId).isEmpty()) {
                ActiveChat activeChat = new ActiveChat();
                activeChat.setChatId(chatId);
                activeChatRepository.save(activeChat);
            }
        } catch (Exception exc) {
            log.error("Возникла неизвестная ошибка, сообщите администратору", exc);
        }
    }

    public void sendNotificationToAllActiveChats(String message, Set<Long> chatIds) {
        for (Long id : chatIds) {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(id));
            sendMessage.setText(message);
            try {
                execute(sendMessage);
            } catch (TelegramApiException e) {
                log.error("Не удалось отправить сообщение");
            }
        }
    }

    private void putPreviousCommand(Long chatId, String command) {
        if (previousCommands.get(chatId) == null) {
            List<String> commands = new ArrayList<>();
            commands.add(command);
            previousCommands.put(chatId, commands);
        } else {
            previousCommands.get(chatId).add(command);
        }
    }

    private String getPreviousCommand(Long chatId) {
        return previousCommands.get(chatId)
                .get(previousCommands.get(chatId).size() - 1);
    }

    @PostConstruct
    public void start() {
        log.info("username:{}, token:{}", name, apiKey);
    }

    @Override
    public String getBotUsername() {
        return name;
    }

    @Override
    public String getBotToken() {
        return apiKey;
    }
}
