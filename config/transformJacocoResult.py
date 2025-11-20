import csv
import sys
import re

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

    tableStyle = "style=\"border: 1px solid black; border-collapse: collapse; padding: 3px;\""
    html_content = f"""
    <html>
    <body>
        <table style="border: 1px solid black; border-collapse: collapse;">
        <thead>
        <tr style="border: 1px solid black; border-collapse: collapse;">
            <th {tableStyle}>Group</th>
            <th {tableStyle}>Package</th>
            <th {tableStyle}>Class</th>
            <th {tableStyle}>Instructions (covered : missed)</th>
            <th {tableStyle}>Instructions Coverage</th>
            <th {tableStyle}>Branches (covered : missed)</th>
            <th {tableStyle}>Branches Coverage</th>
        </tr>
        </thead><tbody>"""

    totalBranchCovered = 0
    totalBranchMissed = 0
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
        if instructionsCovered + instructionsMissed == 0:
            instructionPercent = -100
        else:
            instructionPercent = round(instructionsCovered * 100 / (instructionsCovered + instructionsMissed), 2)
        if branchMissed + branchCovered == 0:
            branchPercent = -100
        else:
            branchesPercent = round(branchCovered * 100 / (branchMissed + branchCovered), 2)
        html_content += f"""
             <tr style="border: 1px solid black; border-collapse: collapse;">
                <td {tableStyle}>{group}</td>
                <td {tableStyle}>{package}</td>
                <td {tableStyle}>{clazz}</td>
                <td {tableStyle}>{instructionsCovered} : {instructionsMissed}</td>
                <td {tableStyle}><span style="color:{style_for_percent(instructionPercent)}">{instructionPercent}%</span></td>
                <td {tableStyle}>{branchCovered} : {branchMissed}</td>
                <td {tableStyle}><span style="color:{style_for_percent(branchesPercent)}">{branchesPercent}%</span></td>
              </tr>
              """

    totalCov = round(totalBranchCovered * 100 / (totalBranchCovered + totalBranchMissed), 2)
    html_content += f"""
    </tbody></table>
    <h1 style="color: {style_for_percent(totalCov)};">Total Branch Coverage: {totalCov}%</h1>
    </body>
    </html>"""

    try:
        with open(output_filepath, 'w', encoding='utf-8') as outfile:
            # Clean output, so GitHub will render it
            text_to_file = re.sub(r'\s+', ' ', html_content.replace("\n",""))
            text_to_file = text_to_file.replace("> <", "><")
            outfile.write(text_to_file)
    except Exception as e:
        print(f"An error occurred while writing the file: {e}", file=sys.stderr)


def style_for_percent(percentage: float) -> str:
    return "red" if percentage < 0.5 else "green"


if __name__ == "__main__":
    input_file = "build/reports/jacoco/test/jacocoTestReport.csv"
    output_file = "build/reports/jacoco/test/output_table.html"

    convert_csv_to_html(input_file, output_file)
