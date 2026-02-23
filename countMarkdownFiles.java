public class countMarkdownFiles {
    Map<String, Integer> counts = new HashMap<>();
    try {
        Files.walk(Paths.get(directory))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".md"))
                .forEach(path -> {
                    String dir = path.getParent().toString();
                    counts.put(dir, counts.getOrDefault(dir, 0) + 1);
                });
    } catch (IOException e) {
        e.printStackTrace();
    }
    return counts;
}
