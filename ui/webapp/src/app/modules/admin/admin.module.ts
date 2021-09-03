import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatToolbarModule } from '@angular/material/toolbar';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { NgTerminalModule } from 'ng-terminal';
import { MonacoEditorModule } from 'ngx-monaco-editor';
import { CoreModule } from '../core/core.module';
import { AdminRoutingModule } from './admin-routing.module';
import { AdminShellComponent } from './components/admin-shell/admin-shell.component';
import { AuditLogComponent } from './components/audit-log/audit-log.component';
import { AuditLogsBrowserComponent } from './components/audit-logs-browser/audit-logs-browser.component';
import { CustomAttributeEditComponent } from './components/custom-attribute-edit/custom-attribute-edit.component';
import { FileEditorComponent } from './components/file-editor/file-editor.component';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { FileViewerComponent } from './components/file-viewer/file-viewer.component';
import { HiveAuditLogsBrowserComponent } from './components/hive-audit-logs-browser/hive-audit-logs-browser.component';
import { HiveBrowserComponent } from './components/hive-browser/hive-browser.component';
import { HiveComponent } from './components/hive/hive.component';
import { LogFilesBrowserComponent } from './components/log-files-browser/log-files-browser.component';
import { MasterCleanupGroupComponent } from './components/master-cleanup-group/master-cleanup-group.component';
import { MasterCleanupComponent } from './components/master-cleanup/master-cleanup.component';
import { MessageboxComponent } from './components/messagebox/messagebox.component';
import { MetricsOverviewComponent } from './components/metrics-overview/metrics-overview.component';
import { SettingsAuthTestUserComponent } from './components/settings-auth-test-user/settings-auth-test-user.component';
import { AttributesTabComponent } from './components/settings-general/attributes-tab/attributes-tab.component';
import { AuthTestComponent } from './components/settings-general/auth-test/auth-test.component';
import { GeneralTabComponent } from './components/settings-general/general-tab/general-tab.component';
import { LdapTabComponent } from './components/settings-general/ldap-tab/ldap-tab.component';
import { PluginDeleteActionComponent } from './components/settings-general/plugins-tab/plugin-delete-action/plugin-delete-action.component';
import { PluginLoadActionComponent } from './components/settings-general/plugins-tab/plugin-load-action/plugin-load-action.component';
import { PluginsTabComponent } from './components/settings-general/plugins-tab/plugins-tab.component';
import { SettingsGeneralComponent } from './components/settings-general/settings-general.component';
import { TextboxComponent } from './components/textbox/textbox.component';
import { UpdateBrowserComponent } from './components/update-browser/update-browser.component';
import { UpdateCardComponent } from './components/update-card/update-card.component';
import { UpdateDialogComponent } from './components/update-dialog/update-dialog.component';
import { UserEditComponent } from './components/user-edit/user-edit.component';
import { UserGlobalPermissionsComponent } from './components/user-global-permissions/user-global-permissions.component';
import { UserPasswordComponent } from './components/user-password/user-password.component';
import { UsersBrowserComponent } from './components/users-browser/users-browser.component';
import { MessageboxService } from './services/messagebox.service';

@NgModule({
  declarations: [
    AuditLogComponent,
    AuditLogsBrowserComponent,
    HiveComponent,
    HiveBrowserComponent,
    HiveAuditLogsBrowserComponent,
    LogFilesBrowserComponent,
    UpdateBrowserComponent,
    UpdateCardComponent,
    UpdateDialogComponent,
    MasterCleanupComponent,
    MetricsOverviewComponent,
    AdminShellComponent,
    SettingsAuthTestUserComponent,
    SettingsGeneralComponent,
    UsersBrowserComponent,
    UserGlobalPermissionsComponent,
    MasterCleanupGroupComponent,
    UserPasswordComponent,
    UserEditComponent,
    UsersBrowserComponent,
    MessageboxComponent,
    FileUploadComponent,
    TextboxComponent,
    CustomAttributeEditComponent,
    FileEditorComponent,
    FileViewerComponent,
    GeneralTabComponent,
    AttributesTabComponent,
    PluginsTabComponent,
    PluginLoadActionComponent,
    PluginDeleteActionComponent,
    LdapTabComponent,
    AuthTestComponent,
  ],
  imports: [
    CommonModule,
    CoreModule,
    AdminRoutingModule,
    NgxChartsModule,

    MatDialogModule,
    FormsModule,
    ReactiveFormsModule,

    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatSlideToggleModule,
    MatSortModule,
    MatTabsModule,
    MatSidenavModule,
    MatPaginatorModule,
    MatSortModule,
    MatMenuModule,
    MatChipsModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatToolbarModule,
    MatProgressBarModule,
    MatCardModule,
    MatListModule,

    NgTerminalModule,
    MonacoEditorModule.forRoot(),
  ],
  providers: [MessageboxService],
})
export class AdminModule {}
