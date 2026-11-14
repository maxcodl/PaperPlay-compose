; Per-user installer: asks to close a running PaperPlay, offers to launch after install.
; Build: ISCC desktopApp\installer\PaperPlay.iss /DAppDir=<main-release\app\PaperPlay> /DOutDir=<folder>
#ifndef AppDir
  #define AppDir "..\build\compose\binaries\main-release\app\PaperPlay"
#endif
#ifndef OutDir
  #define OutDir "..\build\installer"
#endif
#ifndef AppVersion
  #define AppVersion "1.3.0"
#endif

[Setup]
AppId={{6D0C7E52-5A5B-4C47-9D8E-3B7A1F2C9E10}
AppName=PaperPlay
AppVersion={#AppVersion}
AppPublisher=PaperPlay
DefaultDirName={autopf}\PaperPlay
DefaultGroupName=PaperPlay
PrivilegesRequired=lowest
CloseApplications=yes
RestartApplications=no
UninstallDisplayIcon={app}\PaperPlay.exe
SetupIconFile=..\src\jvmMain\resources\app.ico
OutputDir={#OutDir}
OutputBaseFilename=PaperPlay-{#AppVersion}-setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
DisableProgramGroupPage=yes

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; Flags: unchecked

[Files]
Source: "{#AppDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\PaperPlay"; Filename: "{app}\PaperPlay.exe"
Name: "{autodesktop}\PaperPlay"; Filename: "{app}\PaperPlay.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\PaperPlay.exe"; Description: "Launch PaperPlay"; Flags: nowait postinstall skipifsilent

[Code]
{ An older release shipped a per-machine MSI in Program Files. It does not upgrade this per-user install, so tell the user to remove it. }
function InitializeSetup(): Boolean;
begin
  Result := True;
  if (not WizardSilent) and FileExists(ExpandConstant('{commonpf64}\PaperPlay\PaperPlay.exe')) then
    MsgBox('An older PaperPlay is installed in ' + ExpandConstant('{commonpf64}\PaperPlay') + '.' + #13#10#13#10 +
           'After this setup finishes, remove it from Settings > Apps so only one copy is left. Your library and login are kept.',
           mbInformation, MB_OK);
end;

{ After the app is removed, offer to erase the library, Telegram session and secrets in %APPDATA%\PaperPlay. Silent uninstalls keep them. }
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  DataDir: String;
begin
  if (CurUninstallStep = usPostUninstall) and (not UninstallSilent) then
  begin
    DataDir := ExpandConstant('{userappdata}\PaperPlay');
    if DirExists(DataDir) then
      if MsgBox('Also delete all PaperPlay data?' + #13#10#13#10 +
                'This removes your library database, Telegram login and saved bot tokens from:' + #13#10 + DataDir + #13#10#13#10 +
                'Choose No to keep it for a later reinstall.',
                mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
        DelTree(DataDir, True, True, True);
  end;
end;
