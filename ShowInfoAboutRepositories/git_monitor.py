#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Git仓库监控脚本
功能：
1. 实时监控Git仓库中Markdown文件的变化
2. 当检测到文件添加、修改或删除时，自动执行可视化脚本
3. 持续运行，提供实时的可视化更新
"""

import os
import time
import subprocess


def get_md_files(repo_path):
    """
    获取仓库中所有Markdown文件的路径和修改时间
    
    Args:
        repo_path: Git仓库路径
    
    Returns:
        dict: 键为文件路径，值为修改时间
    """
    md_files = {}
    for root, dirs, files in os.walk(repo_path):
        for file in files:
            if file.endswith('.md'):
                file_path = os.path.join(root, file)
                try:
                    mod_time = os.path.getmtime(file_path)
                    md_files[file_path] = mod_time
                except Exception as e:
                    print(f"获取文件修改时间失败: {file_path}, 错误: {e}")
    return md_files


def main():
    """
    主函数，启动监控
    """
    # 获取当前脚本所在目录的绝对路径
    current_dir = os.path.abspath(os.path.dirname(__file__))
    
    # 仓库路径（绝对路径）
    repo_path = os.path.join(current_dir, "Java-Store-to-Surge")
    # 可视化脚本路径（绝对路径）
    visualizer_script = os.path.join(current_dir, "markdown_visualizer.py")
    
    # 检查路径是否存在
    if not os.path.exists(repo_path):
        print(f"错误: 仓库路径 '{repo_path}' 不存在")
        return
    
    if not os.path.exists(visualizer_script):
        print(f"错误: 可视化脚本 '{visualizer_script}' 不存在")
        return
    
    print(f"监控已启动，仓库路径: {repo_path}")
    print(f"可视化脚本: {visualizer_script}")
    print("当检测到Markdown文件变化时，将自动更新可视化结果...")
    print("按 Ctrl+C 停止监控")
    print("=" * 80)
    
    # 初始文件状态
    previous_files = get_md_files(repo_path)
    print(f"初始状态: 发现 {len(previous_files)} 个Markdown文件")
    print("=" * 80)
    
    # 执行间隔，避免频繁执行
    check_interval = 2  # 秒
    last_execution = 0
    execution_cooldown = 2  # 执行冷却时间
    
    try:
        while True:
            # 等待检查间隔
            time.sleep(check_interval)
            
            # 获取当前文件状态
            current_files = get_md_files(repo_path)
            
            # 检查是否有变化
            files_changed = False
            
            # 检查新增或修改的文件
            for file_path, mod_time in current_files.items():
                if file_path not in previous_files:
                    print(f"\n检测到新增Markdown文件: {file_path}")
                    files_changed = True
                elif mod_time != previous_files[file_path]:
                    print(f"\n检测到修改Markdown文件: {file_path}")
                    files_changed = True
            
            # 检查删除的文件
            for file_path in previous_files:
                if file_path not in current_files:
                    print(f"\n检测到删除Markdown文件: {file_path}")
                    files_changed = True
            
            # 如果有变化，执行可视化脚本
            if files_changed:
                current_time = time.time()
                if current_time - last_execution >= execution_cooldown:
                    last_execution = current_time
                    print("正在更新可视化结果...")
                    
                    # 执行可视化脚本
                    try:
                        # 使用绝对路径并确保工作目录正确
                        result = subprocess.run(
                            ['python', visualizer_script],
                            cwd=current_dir,  # 使用当前脚本所在目录作为工作目录
                            capture_output=True,
                            text=True,
                            timeout=30  # 设置超时，避免脚本卡住
                        )
                        
                        # 打印执行结果
                        if result.returncode == 0:
                            print("可视化结果更新成功！")
                            # 提取并打印关键信息
                            output_lines = result.stdout.strip().split('\n')
                            for line in output_lines:
                                if '总Markdown文件数量' in line:
                                    print(f"{line}")
                        else:
                            print(f"可视化脚本执行失败，错误码: {result.returncode}")
                            print(f"错误输出: {result.stderr}")
                    
                    except Exception as e:
                        print(f"执行可视化脚本时出错: {e}")
                    
                    print("=" * 80)
                    # 更新文件状态
                    previous_files = current_files
    
    except KeyboardInterrupt:
        # 按 Ctrl+C 停止监控
        print("\n正在停止监控...")
        print("监控已停止")
    
    except Exception as e:
        print(f"监控过程中出错: {e}")


if __name__ == "__main__":
    main()
