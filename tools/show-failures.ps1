$j = Get-Content "target\cucumber-report.json" -Raw | ConvertFrom-Json
foreach ($f in $j) {
  foreach ($el in $f.elements) {
    $bad = @()
    foreach ($s in $el.steps) {
      $st = $s.result.status
      if ($st -eq 'failed' -or $st -eq 'undefined') {
        $bad += $s
      }
    }
    if ($bad.Count -gt 0) {
      Write-Output ("SCENARIO: " + $el.name + "  [" + $el.id + "]")
      foreach ($s in $bad) {
        Write-Output ("  STEP: " + $s.keyword + $s.name + " => " + $s.result.status)
        if ($s.result.error_message) {
          $lines = $s.result.error_message -split "`n"
          $take = [Math]::Min(4, $lines.Count)
          Write-Output ("    ERR: " + ($lines[0..($take-1)] -join ' ~ '))
        }
      }
      Write-Output ""
    }
  }
}
