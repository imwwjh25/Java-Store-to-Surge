## Goals

1. 因为Repositories的目录太多并且文件数量不少，所以为了统计Repositories的文件数量和分布，并且用可视化的方式展示，所以用Python实现了一个可视化脚本，具体看[markdown_visualizer](./ShowInfoAboutRepositories/markdown_visualizer.py)
2. 为了解决每次git添加操作之后都要手动的执行Python脚本来统计文件数量，所以写了一个Git Hook脚本[pre-commit](./ShowInfoAboutRepositories/git_hooks/pre-commit)，在每次提交之前自动执行Python脚本来更新可视化结果。


## Use

- 启动监控：运行 python git_monitor.py
- 监控脚本会在后台持续运行，检测仓库中Markdown文件的变化
- 当执行 git add 或直接修改Markdown文件时，监控脚本会自动更新可视化结果
- 可视化结果保存在 output 目录中，包括柱状图和HTML报告
- 按 Ctrl+C 停止监控