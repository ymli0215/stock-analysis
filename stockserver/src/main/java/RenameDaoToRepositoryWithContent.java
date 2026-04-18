import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class RenameDaoToRepositoryWithContent {
	public static void main(String[] args) {
		// 設定目標資料夾路徑（請替換為實際路徑）
		String folderPath = "D:\\00_Git\\00_ymli0215\\stockapp\\stockserver\\src\\main\\java\\com\\stockapp\\stockserver\\repo";

		// 取得資料夾
		File folder = new File(folderPath);
		if (!folder.exists() || !folder.isDirectory()) {
			System.out.println("資料夾 " + folderPath + " 不存在或不是資料夾！");
			return;
		}

		// 取得所有 .java 檔案
		File[] files = folder.listFiles((dir, name) -> name.endsWith("Dao.java"));
		if (files == null || files.length == 0) {
			System.out.println("未找到以 Dao 結尾的 .java 檔案！");
			return;
		}

		// 遍歷並重新命名
		for (File file : files) {
			String oldName = file.getName();
			String newName = oldName.replace("Dao.java", "Repository.java");
			Path oldPath = file.toPath();
			Path newPath = Paths.get(file.getParent(), newName);

			try {
				// 修改檔案內容
				List<String> lines = Files.readAllLines(oldPath, StandardCharsets.UTF_8);
				String className = oldName.replace(".java", "");
				String newClassName = className.replace("Dao", "Repository");
				for (int i = 0; i < lines.size(); i++) {
					lines.set(i, lines.get(i).replace(className, newClassName));
				}
				Files.write(newPath, lines, StandardCharsets.UTF_8);

				// 重新命名檔案
				Files.move(oldPath, newPath);
				System.out.println("已將 " + oldName + " 重新命名為 " + newName + " 並更新內容");
			} catch (IOException e) {
				System.err.println("無法處理 " + oldName + "：" + e.getMessage());
			}
		}

		System.out.println("檔案重新命名和內容更新完成！");
	}
}