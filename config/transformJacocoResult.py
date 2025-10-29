import csv
import sys

def convert_csv_to_html(csv_filepath, output_filepath="output_table.html"):
    """
    Reads a Jacoco CSV file and converts its contents into a HTML table.
    """
    try:
        # Open CSV file using 'csv' module for reliable parsing
        with open(csv_filepath, 'r', newline='', encoding='utf-8') as infile:
            reader = csv.reader(infile)

            try:
                next(reader)
            except StopIteration:
                print(f"Error: CSV file '{csv_filepath}' is empty.", file=sys.stderr)
                return

            data_rows = list(reader)

    except FileNotFoundError:
        print(f"Error: Input file '{csv_filepath}' not found.", file=sys.stderr)
        return
    except Exception as e:
        print(f"An error occurred while reading the CSV file: {e}", file=sys.stderr)
        return

    tableStyle = "style=\"border: 1px solid black; border-collapse: collapse; padding: 5px;\""
    html_content = f"""
    <?xml version="1.0" encoding="UTF-8"?><!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
    <html xmlns="http://www.w3.org/1999/xhtml" lang="pl">
    <head>
    <meta http-equiv="Content-Type" content="text/html;charset=UTF-8"/>
    <title>hazelcast-spring-session</title>
    </head>
    <body>
        <table style="border: 1px solid black; border-collapse: collapse;">
        <thead>
        <tr style="border: 1px solid black; border-collapse: collapse;">
            <th {tableStyle}>Group</th>
            <th {tableStyle}>Package</th>
            <th {tableStyle}>Class</th>
            <th {tableStyle}>Instructions</th>
            <th {tableStyle}>Instructions Coverage</th>
            <th {tableStyle}>Branch</th>
            <th {tableStyle}>Branch Coverage</th>
        </tr>
        </thead><tbody>"""

    totalBranchCovered = 0
    totalBranchMissed = 0
    # 3. Generate Table Body (<td>)
    for row in data_rows:
        # GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED
        group = row[0]
        package = row[1]
        clazz = row[2]
        instructionsMissed = int(row[3])
        instructionsCovered = int(row[4])
        branchMissed = int(row[5])
        branchCovered = int(row[6])
        totalBranchCovered += branchCovered
        totalBranchMissed += branchMissed
        html_content +=f"""
                     <tr style="border: 1px solid black; border-collapse: collapse;">
                        <td {tableStyle}>{group}</td>
                        <td {tableStyle}>{package}</td>
                        <td {tableStyle}>{clazz}</td>
                        <td {tableStyle}>{instructionsCovered} : {instructionsMissed}</td>
                        <td {tableStyle}>{round(instructionsCovered * 100 / (instructionsCovered+instructionsMissed), 2)}%</td>
                        <td {tableStyle}>{branchCovered} : {branchMissed}</td>
                        <td {tableStyle}>{round(branchCovered * 100 / (branchMissed+branchCovered), 2)}%</td>
                      </tr>
                    """

    totalCov = round(totalBranchCovered * 100 / (totalBranchCovered + totalBranchMissed), 2)
    style =  "red" if totalCov < 0.5 else "darkgreen"
    html_content += f"""
    </tbody></table>
    <h1 style="color: {style}">Total Branch Coverage: {totalCov}</h1>
    </body>
    </html>
    """

    try:
        with open(output_filepath, 'w', encoding='utf-8') as outfile:
            outfile.write(html_content)
    except Exception as e:
        print(f"An error occurred while writing the file: {e}", file=sys.stderr)


if __name__ == "__main__":
    input_file = "build/reports/jacoco/test/jacocoTestReport.csv"
    output_file = "build/reports/jacoco/test/output_table.html"

    convert_csv_to_html(input_file, output_file)
