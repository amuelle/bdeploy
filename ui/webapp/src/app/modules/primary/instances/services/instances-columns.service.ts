import { Injectable } from '@angular/core';
import { BdDataColumn, BdDataColumnDisplay, BdDataColumnTypeHint } from 'src/app/models/data';
import { InstanceDto, MinionMode } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { InstanceProductVersionComponent } from '../components/instance-product-version/instance-product-version.component';
import { InstancesService } from './instances.service';

@Injectable({
  providedIn: 'root',
})
export class InstancesColumnsService {
  instanceTypeColumn: BdDataColumn<InstanceDto> = {
    id: 'type',
    name: 'Type',
    hint: BdDataColumnTypeHint.TYPE,
    data: (r) => r.instanceConfiguration.purpose,
    width: '150px',
    showWhen: '(min-width: 1000px)',
  };

  instanceNameColumn: BdDataColumn<InstanceDto> = {
    id: 'name',
    name: 'Name',
    hint: BdDataColumnTypeHint.TITLE,
    data: (r) => r.instanceConfiguration.name,
  };

  instanceIdColumn: BdDataColumn<InstanceDto> = {
    id: 'id',
    name: 'ID',
    hint: BdDataColumnTypeHint.DESCRIPTION,
    data: (r) => r.instanceConfiguration.uuid,
    width: '110px',
    showWhen: '(min-width: 1280px)',
  };

  instanceDescriptionColumn: BdDataColumn<InstanceDto> = {
    id: 'description',
    name: 'Description',
    hint: BdDataColumnTypeHint.FOOTER,
    data: (r) => r.instanceConfiguration.description,
    showWhen: '(min-width: 1280px)',
  };

  instanceProductColumn: BdDataColumn<InstanceDto> = {
    id: 'product',
    name: 'Product',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => (!!r.productDto?.name ? r.productDto.name : r.instanceConfiguration.product.name),
    icon: (r) => 'apps',
    showWhen: '(min-width: 600px)',
  };

  instanceProductVersionColumn: BdDataColumn<InstanceDto> = {
    id: 'productVersion',
    name: 'Product Version',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => r.instanceConfiguration.product.tag,
    component: InstanceProductVersionComponent,
    icon: (r) => 'security_update_good',
    width: '150px',
  };

  instanceProductActiveColumn: BdDataColumn<InstanceDto> = {
    id: 'activeProductVersion',
    name: 'Active Version',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => (!!r.activeProductDto ? r.activeProductDto.key.tag : null),
    icon: (r) => 'security_update_good',
    showWhen: '(min-width: 750px)',
    width: '150px',
  };

  instanceServerColumn: BdDataColumn<InstanceDto> = {
    id: 'managedServer',
    name: 'Managed Server',
    hint: BdDataColumnTypeHint.DETAILS,
    data: (r) => (!!r.managedServer ? r.managedServer.description : null),
    icon: (r) => 'dns',
    showWhen: '(min-width: 650px)',
  };

  instanceSyncColumn: BdDataColumn<InstanceDto> = {
    id: 'sync',
    name: 'Sync.',
    hint: BdDataColumnTypeHint.ACTIONS,
    data: (r) => `Synchronize ${r.instanceConfiguration.name}`,
    action: (r) => this.instances.synchronize(r.instanceConfiguration.uuid),
    classes: (r) => (this.instances.isSynchronized(r.instanceConfiguration.uuid) ? [] : ['bd-text-warn']),
    icon: (r) => 'history',
    width: '50px',
  };

  defaultInstancesColumns: BdDataColumn<InstanceDto>[] = [
    this.instanceNameColumn,
    this.instanceTypeColumn,
    this.instanceIdColumn,
    this.instanceDescriptionColumn,
    this.instanceProductColumn,
    this.instanceProductVersionColumn,
    this.instanceProductActiveColumn,
    this.instanceServerColumn,
    this.instanceSyncColumn,
  ];

  constructor(private cfg: ConfigService, private instances: InstancesService) {
    if (cfg.config.mode !== MinionMode.CENTRAL) {
      this.instanceSyncColumn.display = BdDataColumnDisplay.NONE;
      this.instanceServerColumn.display = BdDataColumnDisplay.NONE;
    }
  }
}