"""
SE Unit Test Report Generator - Standard Service Sheet Generator
Module: docs/requirements/generate_service_unit_test_sheet.py
Standard: IEEE 829 / ISO 29119 compliant Unit Test Specification

Usage:
    python docs/requirements/generate_service_unit_test_sheet.py --service JobService
"""

import openpyxl
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation

# Common styles
NAVY_HEADER_FILL = PatternFill(start_color="1F4E78", end_color="1F4E78", fill_type="solid")
NAVY_HEADER_FONT = Font(name="Calibri", size=11, bold=True, color="FFFFFF")
METHOD_BANNER_FILL = PatternFill(start_color="D9E1F2", end_color="D9E1F2", fill_type="solid")
METHOD_BANNER_FONT = Font(name="Calibri", size=10, bold=True, color="1F4E78")

KPI_LABEL_FONT = Font(name="Calibri", size=10, bold=True, color="000000")
KPI_HEADER_FILL = PatternFill(start_color="F2F2F2", end_color="F2F2F2", fill_type="solid")

NO_FILL = PatternFill(fill_type=None)
REGULAR_FONT = Font(name="Calibri", size=10, bold=False, color="000000")
DEFECT_FONT = Font(name="Calibri", size=10, bold=True, color="C00000")

CENTER_ALIGN = Alignment(horizontal="center", vertical="center")
LEFT_ALIGN = Alignment(horizontal="left", vertical="center", wrap_text=True)

THIN_BORDER = Border(
    left=Side(style='thin', color='D3D3D3'),
    right=Side(style='thin', color='D3D3D3'),
    top=Side(style='thin', color='D3D3D3'),
    bottom=Side(style='thin', color='D3D3D3')
)
HEADER_BORDER = Border(
    left=Side(style='thin', color='FFFFFF'),
    right=Side(style='thin', color='FFFFFF'),
    top=Side(style='thin', color='1F4E78'),
    bottom=Side(style='medium', color='1F4E78')
)

def create_or_update_service_sheet(workbook_path, sheet_name, service_name, test_methods):
    """
    Creates or updates a service detail sheet adhering strictly to the IEEE 829 standard.
    
    test_methods format:
    [
        {
            "method_signature": "1. createJob(CreateJobRequest request)",
            "test_cases": [
                {
                    "id": "UTC001",
                    "precondition": "@Mock JobRepository, SecurityContext role=EVENT_ORGANIZER",
                    "input": "title='Event Staff', slots=5, wage=250000",
                    "expected": "Return JobDto(id=..., status=OPEN)\nSide effect: jobRepository.save() invoked once",
                    "type": "Normal", # Normal, Abnormal, Boundary
                    "round1": "Passed", # Passed, Failed, Pending, N/A
                    "date1": "2026-10-25",
                    "tester1": "minhtb",
                    "round2": "Pending",
                    "date2": "2026-11-01",
                    "tester2": "minhtb",
                    "round3": "Pending",
                    "date3": "2026-11-05",
                    "tester3": "minhtb",
                    "defect_id": None,
                    "note_fix": None
                },
                ...
            ]
        },
        ...
    ]
    """
    wb = openpyxl.load_workbook(workbook_path)
    
    if sheet_name in wb.sheetnames:
        ws = wb[sheet_name]
    else:
        ws = wb.create_sheet(title=sheet_name)
        
    ws.views.sheetView[0].showGridLines = True
    
    # 1. Setup Column T Lookup Table
    ws['T2'] = "Passed"
    ws['T3'] = "Failed"
    ws['T4'] = "Pending"
    ws['T5'] = "N/A"
    
    # 2. KPI Summary Header (Rows 2 - 9)
    ws['A2'] = "Code Module"
    ws['B2'] = service_name
    ws['A2'].font = KPI_LABEL_FONT
    ws['B2'].font = KPI_LABEL_FONT
    
    ws['A3'] = "Number of TCs"
    ws['B3'] = '=COUNTIF(A13:A500,"UTC*")'
    ws['A3'].font = KPI_LABEL_FONT
    ws['B3'].font = KPI_LABEL_FONT
    
    ws['A4'] = "Testing Round"
    ws['B4'] = "Passed"
    ws['C4'] = "Failed"
    ws['D4'] = "Pending"
    for c in ['A', 'B', 'C', 'D']:
        cell = ws[f'{c}4']
        cell.font = KPI_LABEL_FONT
        cell.fill = KPI_HEADER_FILL
        cell.alignment = CENTER_ALIGN
        cell.border = THIN_BORDER
        
    ws['A5'] = "Round 1"
    ws['B5'] = '=COUNTIF($F13:$F500,B4)'
    ws['C5'] = '=COUNTIF($F13:$F500,C4)'
    ws['D5'] = '=COUNTIF($F13:$F500,D4)'
    
    ws['A6'] = "Round 2"
    ws['B6'] = '=COUNTIF($I13:$I500,B4)'
    ws['C6'] = '=COUNTIF($I13:$I500,C4)'
    ws['D6'] = '=COUNTIF($I13:$I500,D4)'
    
    ws['A7'] = "Round 3 (Final)"
    ws['B7'] = '=COUNTIF($L13:$L500,B4)'
    ws['C7'] = '=COUNTIF($L13:$L500,C4)'
    ws['D7'] = '=COUNTIF($L13:$L500,D4)'
    
    for r in range(5, 8):
        for c in range(1, 5):
            cell = ws.cell(r, c)
            cell.font = REGULAR_FONT
            cell.alignment = CENTER_ALIGN
            cell.border = THIN_BORDER
            
    ws['A8'] = "Testing Type"
    ws['B8'] = "Normal"
    ws['C8'] = "Abnormal"
    ws['D8'] = "Boundary"
    for c in ['A', 'B', 'C', 'D']:
        cell = ws[f'{c}8']
        cell.font = KPI_LABEL_FONT
        cell.fill = KPI_HEADER_FILL
        cell.alignment = CENTER_ALIGN
        cell.border = THIN_BORDER
        
    ws['A9'] = "Count"
    ws['B9'] = '=COUNTIF($E13:$E500,B8)'
    ws['C9'] = '=COUNTIF($E13:$E500,C8)'
    ws['D9'] = '=COUNTIF($E13:$E500,D8)'
    for c in range(1, 5):
        cell = ws.cell(9, c)
        cell.font = REGULAR_FONT
        cell.alignment = CENTER_ALIGN
        cell.border = THIN_BORDER
        
    # 3. Table Headers (Row 11)
    headers = [
        "Test Case ID", "Pre-conditions (Mocking Setup)", "Input parameters", "Expected Results",
        "Type", "Round 1", "Test date", "Tester", "Round 2", "Test date", "Tester",
        "Round 3", "Test date", "Tester", "Defect IDs", "Note & Root Cause Fix"
    ]
    ws.row_dimensions[11].height = 28
    for col_idx, h_text in enumerate(headers, start=1):
        cell = ws.cell(11, col_idx, h_text)
        cell.fill = NAVY_HEADER_FILL
        cell.font = NAVY_HEADER_FONT
        cell.alignment = CENTER_ALIGN
        cell.border = HEADER_BORDER
        
    # 4. Populate Method Headers and Test Cases
    current_row = 12
    for method in test_methods:
        # Method separator banner
        ws.row_dimensions[current_row].height = 24
        cell = ws.cell(current_row, 1, method["method_signature"])
        cell.font = METHOD_BANNER_FONT
        cell.fill = METHOD_BANNER_FILL
        cell.alignment = LEFT_ALIGN
        
        # Format entire method banner across columns 1 to 16
        for c in range(2, 17):
            empty_cell = ws.cell(current_row, c, None)
            empty_cell.fill = METHOD_BANNER_FILL
            empty_cell.border = THIN_BORDER
        current_row += 1
        
        for tc in method["test_cases"]:
            ws.row_dimensions[current_row].height = 65
            
            ws.cell(current_row, 1, tc["id"]).alignment = CENTER_ALIGN
            ws.cell(current_row, 2, tc["precondition"]).alignment = LEFT_ALIGN
            ws.cell(current_row, 3, tc["input"]).alignment = LEFT_ALIGN
            ws.cell(current_row, 4, tc["expected"]).alignment = LEFT_ALIGN
            ws.cell(current_row, 5, tc["type"]).alignment = CENTER_ALIGN
            
            # Round 1
            ws.cell(current_row, 6, tc.get("round1", "Pending")).alignment = CENTER_ALIGN
            ws.cell(current_row, 7, tc.get("date1", "")).alignment = CENTER_ALIGN
            ws.cell(current_row, 8, tc.get("tester1", "")).alignment = CENTER_ALIGN
            
            # Round 2
            ws.cell(current_row, 9, tc.get("round2", "Pending")).alignment = CENTER_ALIGN
            ws.cell(current_row, 10, tc.get("date2", "")).alignment = CENTER_ALIGN
            ws.cell(current_row, 11, tc.get("tester2", "")).alignment = CENTER_ALIGN
            
            # Round 3
            ws.cell(current_row, 12, tc.get("round3", "Pending")).alignment = CENTER_ALIGN
            ws.cell(current_row, 13, tc.get("date3", "")).alignment = CENTER_ALIGN
            ws.cell(current_row, 14, tc.get("tester3", "")).alignment = CENTER_ALIGN
            
            # Defect & Note
            cell_def = ws.cell(current_row, 15, tc.get("defect_id", None))
            cell_def.alignment = CENTER_ALIGN
            if tc.get("defect_id"):
                cell_def.font = DEFECT_FONT
            else:
                cell_def.font = REGULAR_FONT
                
            ws.cell(current_row, 16, tc.get("note_fix", None)).alignment = LEFT_ALIGN
            
            # Apply standard fonts, zero-fill on rounds, and borders
            for c in range(1, 17):
                cl = ws.cell(current_row, c)
                if c != 15 or not tc.get("defect_id"):
                    cl.font = REGULAR_FONT
                cl.fill = NO_FILL  # CRITICAL: Round columns remain uncolored!
                cl.border = THIN_BORDER
                
            current_row += 1

    # 5. Reset & Apply Data Validations
    ws.data_validations.dataValidation.clear()
    
    dv_round = DataValidation(
        type="list",
        formula1='"Passed,Failed,Pending,N/A"',
        allow_blank=True,
        showDropDown=False
    )
    dv_round.error = "Select a valid status: Passed, Failed, Pending, or N/A."
    dv_round.errorTitle = "Invalid Status"
    dv_round.prompt = "Select status: Passed, Failed, Pending, N/A"
    dv_round.promptTitle = "Test Status"
    
    dv_round.add(f"F13:F{current_row + 50}")
    dv_round.add(f"I13:I{current_row + 50}")
    dv_round.add(f"L13:L{current_row + 50}")
    ws.add_data_validation(dv_round)
    
    dv_type = DataValidation(
        type="list",
        formula1='"Normal,Abnormal,Boundary"',
        allow_blank=True,
        showDropDown=False
    )
    dv_type.error = "Select a valid type: Normal, Abnormal, or Boundary."
    dv_type.errorTitle = "Invalid Type"
    dv_type.prompt = "Select test case type"
    dv_type.promptTitle = "Test Type"
    
    dv_type.add(f"E13:E{current_row + 50}")
    ws.add_data_validation(dv_type)
    
    # Column widths
    col_widths = {
        'A': 14, 'B': 32, 'C': 30, 'D': 42, 'E': 14,
        'F': 12, 'G': 12, 'H': 12, 'I': 12, 'J': 12,
        'K': 12, 'L': 12, 'M': 12, 'N': 12, 'O': 16, 'P': 45
    }
    for col_letter, width in col_widths.items():
        ws.column_dimensions[col_letter].width = width
        
    wb.save(workbook_path)
    print(f"Successfully generated/updated {sheet_name} in {workbook_path}!")

if __name__ == "__main__":
    print("SE Unit Test Sheet Generator loaded successfully.")

