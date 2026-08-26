package com.campus.agent;

import com.campus.agent.service.AssistantService;
import com.campus.agent.store.ItemRepository;
import com.campus.agent.store.StoredItem;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** 命令行入口。带参数运行 = 冒烟模式（每个参数当作一条消息处理完退出）；不带参数 = 交互 REPL。 */
public class Main {

    public static void main(String[] args) throws Exception {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(CampusAgentApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            AssistantService service = ctx.getBean(AssistantService.class);
            ItemRepository repo = ctx.getBean(ItemRepository.class);

            if (args.length > 0) {
                for (String a : args) {
                    System.out.println("你 > " + a);
                    System.out.println("助手 > " + service.handle(a));
                }
                return;
            }

            Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
            System.out.println("校园助手已启动。直接输入消息；/list 查看记录；/help 帮助；/quit 退出。");
            while (true) {
                System.out.print("你 > ");
                if (!in.hasNextLine()) {
                    break;
                }
                String line = in.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                switch (line) {
                    case "/quit", "/exit" -> {
                        System.out.println("再见！");
                        return;
                    }
                    case "/help" -> printHelp();
                    case "/list" -> listAll(repo);
                    default -> System.out.println("助手 > " + service.handle(line));
                }
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
                用法：
                  直接输入消息 → 自动识别是记录还是提问
                  /list  列出数据库所有记录
                  /quit  退出
                示例：
                  高数作业习题5.2，11月20日前交
                  今天有什么课？
                  不对，截止是11月21日
                 """);
    }

    private static void listAll(ItemRepository repo) throws Exception {
        var items = repo.allItems();
        if (items.isEmpty()) {
            System.out.println("（数据库还没有记录）");
            return;
        }
        for (StoredItem s : items) {
            String when = s.dueDate() != null
                    ? s.dueDate() + (s.dueTime() != null ? " " + s.dueTime() : "")
                    : "-";
            System.out.printf("[%s] #%d 《%s》 时间:%s 状态:%s%n",
                    typeLabel(s.type()), s.id(), s.title(), when, s.status());
        }
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case "assignment" -> "作业";
            case "exam" -> "考试";
            case "todo" -> "待办";
            case "course" -> "课程";
            case "event" -> "日程";
            default -> "记录";
        };
    }
}
