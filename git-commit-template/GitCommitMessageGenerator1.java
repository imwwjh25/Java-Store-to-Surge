import java.io.*;
import java.nio.file.*;
import java.util.*;
/**
 * 类描述
 *
 * @author wjh
 * @description
 * @date 2026/2/26 下午4:58
 */
public class GitCommitMessageGenerator1 {

    private static final Map<String, String> DOCS_MAP = new HashMap<>();
    private static final List<String> VERBS = Arrays.asList(
            "添加", "更新", "修正", "补充", "完善",
            "整理", "梳理", "优化", "解释", "分析"
    );

    static {
        DOCS_MAP.put("Java基础", "Java基础");
        DOCS_MAP.put("Java并发", "Java并发编程");
        DOCS_MAP.put("JVM", "Java虚拟机");
        DOCS_MAP.put("MySQL", "Mysql数据库");
        DOCS_MAP.put("Redis", "Redis");
        DOCS_MAP.put("Linux", "Linux");
        DOCS_MAP.put("Maven", "Maven");
        DOCS_MAP.put("Git", "Git");
    }

    public static void main(String[] args) {
        try {
            System.out.println("\n========== Markdown 文档 Commit Message 生成器 ==========\n");

            List<File> markdownFiles = findMarkdownFiles(".");

            if (markdownFiles.isEmpty()) {
                System.out.println("⚠️  未找到 Markdown 文档文件");
                System.out.println("按回车键退出...");
                new Scanner(System.in).nextLine();
                return;
            }

            System.out.println("【检测到的 Markdown 文档】");
            for (int i = 0; i < markdownFiles.size(); i++) {
                File file = markdownFiles.get(i);
                String relativePath = getRelativePath(file);
                System.out.println("  [" + (i + 1) + "] " + relativePath);
            }

            Scanner scanner = new Scanner(System.in);
            System.out.print("\n请选择要生成 commit message 的文件 (多个文件用逗号分隔，如: 1,3,5,6): ");
            String fileChoicesStr = scanner.nextLine();
            String[] fileChoiceArray = fileChoicesStr.split(",");
            List<File> selectedFiles = new ArrayList<>();

            for (String choice : fileChoiceArray) {
                try {
                    int fileChoice = Integer.parseInt(choice.trim());
                    if (fileChoice >= 1 && fileChoice <= markdownFiles.size()) {
                        selectedFiles.add(markdownFiles.get(fileChoice - 1));
                    }
                } catch (NumberFormatException e) {
                    // 忽略无效输入
                }
            }

            if (selectedFiles.isEmpty()) {
                System.out.println("⚠️  未选择有效文件");
                System.out.println("按回车键退出...");
                scanner.nextLine();
                return;
            }

            System.out.println("\n【选择动词】");
            for (int i = 0; i < VERBS.size(); i++) {
                System.out.println("  [" + (i + 1) + "] " + VERBS.get(i));
            }
            System.out.print("\n请选择动词 (1-" + VERBS.size() + ", 默认 1): ");
            String verbChoiceStr = scanner.nextLine();
            int verbChoice = verbChoiceStr.isEmpty() ? 1 : Integer.parseInt(verbChoiceStr);
            String selectedVerb = VERBS.get(verbChoice - 1);

            List<String> commitMessages = new ArrayList<>();
            System.out.println("\n========== 生成的 Commit Message ==========\n");

            for (File selectedFile : selectedFiles) {
                String relativePath = getRelativePath(selectedFile);

                String scope = "其他";
                for (Map.Entry<String, String> entry : DOCS_MAP.entrySet()) {
                    if (relativePath.contains(entry.getValue())) {
                        scope = entry.getKey();
                        break;
                    }
                }

                String title = selectedFile.getName().replace(".md", "");
                String subject = selectedVerb + title;
                String commitMessage = "docs(" + scope + "): " + subject;
                commitMessages.add(commitMessage);
                System.out.println(commitMessage);
            }

            System.out.println("\n============================================\n");

            // 保存到文件
            String outputFile = "git-commit-template-result.txt";
            try (FileWriter writer = new FileWriter(outputFile)) {
                for (String msg : commitMessages) {
                    writer.write(msg);
                    writer.write("\n");
                }
            }
            System.out.println("✅  Commit messages 已保存到: " + outputFile);
            System.out.println("✅  共生成 " + commitMessages.size() + " 条 commit message");

            System.out.println("\n按回车键退出...");
            scanner.nextLine();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<File> findMarkdownFiles(String directory) throws IOException {
        List<File> files = new ArrayList<>();
        Path startPath = Paths.get(directory);

        Files.walk(startPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".md"))
                .filter(path -> !path.toString().contains(".git"))
                .filter(path -> !path.toString().contains("docs/git-commit-template.md"))
                .forEach(path -> files.add(path.toFile()));

        return files;
    }

    private static String getRelativePath(File file) {
        String currentDir = System.getProperty("user.dir").replace("\\", "/");
        String filePath = file.getAbsolutePath().replace("\\", "/");
        if (filePath.startsWith(currentDir)) {
            return filePath.substring(currentDir.length() + 1);
        }
        return filePath;
    }
}