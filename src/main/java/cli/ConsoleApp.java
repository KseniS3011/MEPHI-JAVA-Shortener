package cli;

import java.util.List;
import java.util.Scanner;
import model.Link;
import service.ShortenerService;

public class ConsoleApp {
  private final ShortenerService service;

  public ConsoleApp(ShortenerService service) {
    this.service = service;
  }

  public void run() {
    printHelp();
    Scanner sc = new Scanner(System.in);

    while (true) {
      System.out.print("> ");
      String line = sc.nextLine().trim();
      if (line.isEmpty()) {
        continue;
      }

      String[] parts = line.split("\\s+");
      String cmd = parts[0].toLowerCase();

      try {
        switch (cmd) {
          case "help" -> printHelp();
          case "exit" -> {
            System.out.println("До скорых встреч!");
            return;
          }
          case "create" -> handleCreate(parts);
          case "open" -> handleOpen(parts);
          case "list" -> handleList();
          case "delete" -> handleDelete(parts);
          case "limit" -> handleLimit(parts);
          case "whoami" -> handleWhoAmI();
          case "user" -> handleUser(parts);
          case "newuser" -> handleNewUser();
          default -> System.out.println("Неизвестная команда. Введите 'help' для справки.");
        }
      } catch (Exception e) {
        System.out.println("ОШИБКА: " + e.getMessage());
      }
    }
  }

  private void handleCreate(String[] parts) {
    if (parts.length < 2) {
      System.out.println("Не указан URL. Введите: create <url> [лимит]");
      return;
    }
    String url = parts[1];
    Integer limit = null;
    if (parts.length >= 3) {
      limit = Integer.parseInt(parts[2]);
    }

    Link link = service.create(url, limit);

    System.out.println("Короткая ссылка создана!");
    System.out.println("Ваш UUID: " + service.getCurrentUserUuid());
    System.out.println("Код: " + link.code());
    System.out.println("Короткая ссылка: " + link.shortUrl());
    System.out.println("Срок действия до: " + link.expiresAt());
    System.out.println("Лимит переходов: " + link.maxClicks());
  }

  private void handleOpen(String[] parts) {
    if (parts.length < 2) {
      System.out.println("Не указан код ссылки. Введите: open <код>");
      return;
    }
    service.open(parts[1]);
  }

  private void handleList() {
    List<Link> links = service.listMine();
    if (links.isEmpty()) {
      System.out.println("У вас пока нет созданных ссылок.");
      return;
    }
    for (Link l : links) {
      System.out.println(
          l.code()
              + " -> "
              + l.originalUrl()
              + " | переходы: "
              + l.clicksDone()
              + "/"
              + l.maxClicks()
              + " | истекает: "
              + l.expiresAt());
    }
  }

  private void handleDelete(String[] parts) {
    if (parts.length < 2) {
      System.out.println("Не указан код ссылки. Введите: delete <код>");
      return;
    }
    service.deleteMine(parts[1]);
    System.out.println("Ссылка удалена.");
  }

  private void handleLimit(String[] parts) {
    if (parts.length < 3) {
      System.out.println("Не указан код или новый лимит. Введите: limit <код> <новый_лимит>");
      return;
    }
    String code = parts[1];
    int newLimit = Integer.parseInt(parts[2]);
    service.updateLimitMine(code, newLimit);
    System.out.println("Лимит переходов обновлён.");
  }

  private void handleWhoAmI() {
    System.out.println("Ваш UUID: " + service.getCurrentUserUuid());
  }

  private void handleUser(String[] parts) {
    if (parts.length < 2) {
      System.out.println("Не указан UUID пользователя. Введите: user <uuid>");
      return;
    }
    service.switchUser(parts[1]);
    System.out.println("Пользователь переключён. Текущий UUID: " + service.getCurrentUserUuid());
  }

  private void handleNewUser() {
    service.newUser();
    System.out.println("Создан новый пользователь. Текущий UUID: " + service.getCurrentUserUuid());
  }

  private void printHelp() {
    System.out.println(
        """
                        Команды:
                          create <url> [лимит]        - создать короткую ссылку
                          open <код>                  - перейти по ссылке
                          list                        - список ваших ссылок
                          delete <код>                - удалить ссылку
                          limit <код> <новый_лимит>   - изменить лимит переходов
                          whoami                      - показать текущий UUID
                          user <uuid>                 - переключиться на пользователя
                          newuser                     - создать нового пользователя
                          help                        - меню
                          exit                        - выход
                        """);
  }
}
