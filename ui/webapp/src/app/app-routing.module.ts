import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AttachCentralComponent } from './attach-central/attach-central.component';
import { AttachManagedComponent } from './attach-managed/attach-managed.component';
import { ClientAppsComponent } from './client-apps/client-apps.component';
import { ConfigFilesBrowserComponent } from './config-files-browser/config-files-browser.component';
import { DataFilesBrowserComponent } from './data-files-browser/data-files-browser.component';
import { InstanceAddEditComponent } from './instance-add-edit/instance-add-edit.component';
import { InstanceBrowserComponent } from './instance-browser/instance-browser.component';
import { InstanceGroupAddEditComponent } from './instance-group-add-edit/instance-group-add-edit.component';
import { InstanceGroupBrowserComponent } from './instance-group-browser/instance-group-browser.component';
import { ManagedServersComponent } from './managed-servers/managed-servers.component';
import { LoginComponent } from './modules/core/components/login/login.component';
import { AuthGuard } from './modules/shared/guards/authentication.guard';
import { CanDeactivateGuard } from './modules/shared/guards/can-deactivate.guard';
import { ProcessConfigurationComponent } from './process-configuration/process-configuration.component';
import { ProductSyncComponent } from './product-sync/product-sync.component';
import { ProductsComponent } from './products/products.component';

const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'instancegroup/browser',
  },
  {
    path: 'login',
    component: LoginComponent,
    data: { title: 'Login', header: 'Login' }
  },
  {
    path: 'instancegroup/browser',
    component: InstanceGroupBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Instance Groups', header: 'Instance Groups' }
  },
  {
    path: 'instancegroup/add',
    component: InstanceGroupAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Instance Group', header: 'Add Instance Group' }
  },
  {
    path: 'instancegroup/edit/:name',
    component: InstanceGroupAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Instance Group (${params["name"]})', header: 'Edit Instance Group' }
  },
  {
    path: 'instancegroup/products/:group',
    component: ProductsComponent,
    canActivate: [AuthGuard],
    data: { title: 'Manage Products (${params["group"]})', header: 'Manage Products' }
  },
  {
    path: 'instance/browser/:name',
    component: InstanceBrowserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Instances (${params["name"]})', header: 'Instances' }
  },
  {
    path: 'instance/add/:group',
    component: InstanceAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Add Instance (${params["group"]})', header: 'Add Instance' }
  },
  {
    path: 'instance/edit/:group/:uuid',
    component: InstanceAddEditComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Edit Instance (${params["group"]} - ${params["uuid"]})', header: 'Edit Instance' }
  },
  {
    path: 'instance/overview/:group/:uuid',
    component: ProcessConfigurationComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Instance Overview (${params["group"]} - ${params["uuid"]})', header: 'Instance Overview' }
  },
  {
    path: 'instance/configfiles/:group/:uuid/:version',
    component: ConfigFilesBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Configuration Files (${params["group"]} - ${params["uuid"]})', header: 'Configuration Files' }
  },
  {
    path: 'instance/datafiles/:group/:uuid/:version',
    component: DataFilesBrowserComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Data Files (${params["group"]} - ${params["uuid"]})', header: 'Data Files' }
  },
  {
    path: 'clientapps/:group',
    component: ClientAppsComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Client Applications (${params["group"]})', header: 'Client Applications' }
  },
  {
    path: 'servers/attach/central',
    component: AttachCentralComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Attach to Central Server', header: 'Attach to Central Server' }
  },
  {
    path: 'servers/attach/managed/:group',
    component: AttachManagedComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Attach Managed Server', header: 'Attach Managed Server' }
  },
  {
    path: 'servers/:group',
    component: ManagedServersComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Managed Servers', header: 'Managed Servers' }
  },
  {
    path: 'servers/product-sync/:group',
    component: ProductSyncComponent,
    canActivate: [AuthGuard],
    canDeactivate: [CanDeactivateGuard],
    data: { title: 'Transfer Product Versions (${params["group"]})', header: 'Transfer Product Versions' }
  },
  {
    path: 'admin',
    loadChildren: () => import('./modules/admin/admin.module').then(x => x.AdminModule)
  },
  {
    path: 'softwarerepo',
    loadChildren: () => import('./modules/repositories/repositories.module').then(x => x.RepositoriesModule)
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: true })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
