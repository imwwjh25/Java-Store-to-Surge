//import java.io.*;
//import java.nio.file.*;
//import java.util.*;
//
//public class GitCommitMessageGenerator {
//
//    private static final Map<String, String> DOCS_MAP = new HashMap<>();
//    private static final List<String> VERBS = Arrays.asList(
//        "添加", "更新", "修正", "补充", "完善",
//        "整理", "梳理", "优化", "解释", "分析"
//    );
//
//    static {
//        DOCS_MAP.put("Java基础", "Java基础");
//        DOCS_MAP.put("Java并发", "Java并发编程");
//        DOCS_MAP.put("JVM", "Java虚拟机");
//        DOCS_MAP.put("MySQL", "Mysql数据库");
//        DOCS_MAP.put("Redis", "Redis");
//        DOCS_MAP.put("Linux", "Linux");
//        DOCS_MAP.put("Maven", "Maven");
//        DOCS_MAP.put("Git", "Git");
//    }
//
//    public static void main(String[] args) {
//        try {
//            System.out.println("\n========== Markdown 文档 Commit Message 生成器 ==========\n");
//
//            List<File> markdownFiles = findMarkdownFiles(".");
//
//            if (markdownFiles.isEmpty()) {
//                System.out.println("⚠️  未找到 Markdown 文档文件");
//                System.out.println("按回车键退出...");
//                new Scanner(System.in).nextLine();
//                return;
//            }
//
//            System.out.println("【检测到的 Markdown 文档】");
//            for (int i = 0; i < markdownFiles.size(); i++) {
//                File file = markdownFiles.get(i);
//                String relativePath = getRelativePath(file);
//                System.out.println("  [" + (i + 1) + "] " + relativePath);
//            }
//
//            Scanner scanner = new Scanner(System.in);
//            System.out.print("\n请选择要生成 commit message 的文件 (1-" + markdownFiles.size() + "): ");
//            int fileChoice = scanner.nextInt();
//            scanner.nextLine(); // 消耗换行符
//
//            File selectedFile = markdownFiles.get(fileChoice - 1);
//            String relativePath = getRelativePath(selectedFile);
//
//            String scope = "其他";
//            for (Map.Entry<String, String> entry : DOCS_MAP.entrySet()) {
//                if (relativePath.contains(entry.getValue())) {
//                    scope = entry.getKey();
//                    break;
//                }
//            }
//
//            String title = selectedFile.getName().replace(".md", "");
//
//            System.out.println("\n【自动生成的信息】");
//            System.out.println("  文件: " + relativePath);
//            System.out.println("  范围: " + scope);
//            System.out.println("  标题: " + title);
//
//            System.out.println("\n【选择动词】");
//            for (int i = 0; i < VERBS.size(); i++) {
//                System.out.println("  [" + (i + 1) + "] " + VERBS.get(i));
//            }
//            System.out.print("\n请选择动词 (1-" + VERBS.size() + ", 默认 1): ");
//            String verbChoiceStr = scanner.nextLine();
//            int verbChoice = verbChoiceStr.isEmpty() ? 1 : Integer.parseInt(verbChoiceStr);
//            String selectedVerb = VERBS.get(verbChoice - 1);
//
//            String subject = selectedVerb + title;
//            String commitMessage = "docs(" + scope + "): " + subject;
//
//            System.out.println("\n========== 生成的 Commit Message ==========\n");
//            System.out.println(commitMessage);
//            System.out.println("\n============================================\n");
//
//            // 保存到文件
//            String outputFile = "git-commit-template-result.txt";
//            try (FileWriter writer = new FileWriter(outputFile)) {
//                writer.write(commitMessage);
//                writer.write("\n");
//            }
//            System.out.println("✅  Commit message 已保存到: " + outputFile);
//
//            System.out.println("\n按回车键退出...");
//            scanner.nextLine();
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static List<File> findMarkdownFiles(String directory) throws IOException {
//        List<File> files = new ArrayList<>();
//        Path startPath = Paths.get(directory);
//
//        Files.walk(startPath)
//            .filter(Files::isRegularFile)
//            .filter(path -> path.toString().endsWith(".md"))
//            .filter(path -> !path.toString().contains(".git"))
//            .filter(path -> !path.toString().contains("docs/git-commit-template.md"))
//            .forEach(path -> files.add(path.toFile()));
//
//        return files;
//    }
//
//    private static String getRelativePath(File file) {
//        String currentDir = System.getProperty("user.dir").replace("\\", "/");
//        String filePath = file.getAbsolutePath().replace("\\", "/");
//        if (filePath.startsWith(currentDir)) {
//            return filePath.substring(currentDir.length() + 1);
//        }
//        return filePath;
//    }
//}
