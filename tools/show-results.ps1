$j = Get-Content "target\cucumber-report.json" -Raw | ConvertFrom-Json
foreach ($f in $j) {
  Write-Output ("FEATURE: " + $f.name)
  foreach ($el in $f.elements) {
    $failed = 0
    $passed = 0
    foreach ($s in $el.steps) {
      $st = $s.result.status
      if ($st -eq 'passed') { $passed++ }
      if ($st -eq 'failed' -or $st -eq 'undefined') { $failed++ }
    }
    $verdict = "PASS"
    if ($failed -gt 0) { $verdict = "FAIL" }
    Write-Output ("  [" + $verdict + "] " + $el.name + "  (passed=" + $passed + " failed=" + $failed + ")")
  }
}
