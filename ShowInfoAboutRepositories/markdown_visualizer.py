#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Markdown文件统计与可视化脚本
功能：
1. 递归扫描仓库中的所有目录和子目录，统计各目录下Markdown文件(.md)的数量
2. 生成饼状图可视化展示各目录中Markdown文件数量的占比分布
3. 创建表格展示统计结果，包含目录路径和对应Markdown文件数量，并显示总数量
4. 生成HTML格式的可视化结果文件
"""

import os
import matplotlib.pyplot as plt
import pandas as pd
from matplotlib.font_manager import FontProperties


def count_markdown_files(directory):
    """
    递归统计目录及其子目录下的Markdown文件数量
    
    Args:
        directory: 要扫描的根目录路径
    
    Returns:
        dict: 键为目录路径，值为该目录下的Markdown文件数量
    """
    md_counts = {}
    
    try:
        # 遍历目录及其子目录
        for root, dirs, files in os.walk(directory):
            # 统计当前目录下的Markdown文件数量
            md_count = sum(1 for file in files if file.endswith('.md'))
            if md_count > 0:
                # 计算相对路径（相对于仓库根目录）
                rel_path = os.path.relpath(root, directory)
                if rel_path == '.':
                    rel_path = '根目录'
                md_counts[rel_path] = md_count
    
    except Exception as e:
        print(f"扫描目录时出错: {e}")
    
    return md_counts


def generate_bar_chart(data, output_file):
    """
    生成柱状图并保存
    
    Args:
        data: 字典，键为目录路径，值为Markdown文件数量
        output_file: 输出文件路径
    """
    try:
        # 设置中文字体
        plt.rcParams['font.sans-serif'] = ['SimHei']  # 用来正常显示中文标签
        plt.rcParams['axes.unicode_minus'] = False  # 用来正常显示负号
        
        # 准备数据并按数量降序排序
        sorted_data = sorted(data.items(), key=lambda x: x[1], reverse=True)
        labels = [item[0] for item in sorted_data]
        values = [item[1] for item in sorted_data]
        
        # 创建柱状图
        plt.figure(figsize=(16, 10))
        bars = plt.bar(range(len(labels)), values, color='skyblue')
        plt.xlabel('目录路径', fontsize=12)
        plt.ylabel('Markdown文件数量', fontsize=12)
        plt.title('各目录Markdown文件数量分布', fontsize=16)
        plt.xticks(range(len(labels)), labels, rotation=45, ha='right', fontsize=10)
        plt.tight_layout()
        
        # 在柱状图上添加数值标签
        for bar in bars:
            height = bar.get_height()
            plt.text(bar.get_x() + bar.get_width()/2., height + 0.5,
                    f'{height}', ha='center', va='bottom')
        
        # 保存柱状图
        plt.savefig(output_file, dpi=100, bbox_inches='tight')
        plt.close()
        print(f"柱状图已保存至: {output_file}")
    
    except Exception as e:
        print(f"生成柱状图时出错: {e}")


def generate_html_report(data, bar_chart_path, output_file):
    """
    生成包含表格和柱状图的HTML报告
    
    Args:
        data: 字典，键为目录路径，值为Markdown文件数量
        bar_chart_path: 柱状图图片路径
        output_file: 输出HTML文件路径
    """
    try:
        # 计算总Markdown文件数量
        total_count = sum(data.values())
        
        # 创建DataFrame
        df = pd.DataFrame(list(data.items()), columns=['目录路径', 'Markdown文件数量'])
        df = df.sort_values(by='Markdown文件数量', ascending=False)  # 按数量降序排序
        
        # 生成HTML内容，注意使用双重大括号转义CSS中的大括号
        html_content = '''
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Markdown文件统计报告</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    margin: 20px;
                    background-color: #f5f5f5;
                }
                h1 {
                    color: #333;
                    text-align: center;
                }
                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    background-color: white;
                    padding: 20px;
                    border-radius: 8px;
                    box-shadow: 0 0 10px rgba(0,0,0,0.1);
                }
                .chart-container {
                    text-align: center;
                    margin: 30px 0;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin: 20px 0;
                }
                th, td {
                    padding: 12px;
                    text-align: left;
                    border-bottom: 1px solid #ddd;
                }
                th {
                    background-color: #4CAF50;
                    color: white;
                }
                tr:hover {
                    background-color: #f5f5f5;
                }
                .total-row {
                    font-weight: bold;
                    background-color: #f2f2f2;
                }
                img {
                    max-width: 100%;
                    height: auto;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>Markdown文件统计报告</h1>
                
                <div class="chart-container">
                    <h2>各目录Markdown文件数量分布</h2>
                    <img src="''' + bar_chart_path + '''" alt="Markdown文件数量分布柱状图">
                </div>
                
                <h2>统计详情</h2>
                <table>
                    <tr>
                        <th>目录路径</th>
                        <th>Markdown文件数量</th>
                    </tr>
        '''
        
        # 添加表格数据行
        for _, row in df.iterrows():
            html_content += '''
                    <tr>
                        <td>''' + str(row['目录路径']) + '''</td>
                        <td>''' + str(row['Markdown文件数量']) + '''</td>
                    </tr>
            '''
        
        # 添加总计行
        html_content += '''
                    <tr class="total-row">
                        <td>总计</td>
                        <td>''' + str(total_count) + '''</td>
                    </tr>
                </table>
            </div>
        </body>
        </html>
        '''
        
        # 写入HTML文件
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(html_content)
        
        print(f"HTML报告已生成至: {output_file}")
        print(f"总Markdown文件数量: {total_count}")
    
    except Exception as e:
        print(f"生成HTML报告时出错: {e}")


def main():
    """
    主函数，执行整个流程
    """
    # 仓库路径
    repo_path = "Java-Store-to-Surge"
    
    # 检查仓库路径是否存在
    if not os.path.exists(repo_path):
        print(f"错误: 仓库路径 '{repo_path}' 不存在")
        return
    
    # 创建输出目录
    output_dir = "output"
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
    
    # 步骤1: 统计Markdown文件数量
    print("正在扫描目录并统计Markdown文件数量...")
    md_counts = count_markdown_files(repo_path)
    
    if not md_counts:
        print("错误: 未找到Markdown文件")
        return
    
    # 步骤2: 生成柱状图
    bar_chart_path = os.path.join(output_dir, "markdown_bar_chart.png")
    generate_bar_chart(md_counts, bar_chart_path)
    
    # 步骤3: 生成HTML报告
    html_output_path = os.path.join(output_dir, "markdown_statistics_report.html")
    # 计算相对路径，使HTML中能正确引用图片
    relative_bar_path = os.path.basename(bar_chart_path)  # 直接使用文件名，因为图片和HTML在同一目录
    generate_html_report(md_counts, relative_bar_path, html_output_path)
    # 打印详细信息，方便调试
    print(f"仓库路径: {repo_path}")
    print(f"Markdown文件统计结果: {md_counts}")
    print(f"柱状图路径: {bar_chart_path}")
    print(f"HTML报告路径: {html_output_path}")
    print(f"相对图片路径: {relative_bar_path}")
    
    print("\n统计完成！")


if __name__ == "__main__":
    main()
