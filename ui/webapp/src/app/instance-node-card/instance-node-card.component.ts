import { moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild } from '@angular/core';
import { cloneDeep } from 'lodash';
import { DragulaService } from 'ng2-dragula';
import { Subscription } from 'rxjs';
import { ApplicationGroup } from '../models/application.model';
import { CLIENT_NODE_NAME, EMPTY_APPLICATION_CONFIGURATION, EMPTY_INSTANCE_NODE_CONFIGURATION, EMPTY_PROCESS_CONTROL_CONFIG } from '../models/consts';
import { EventWithCallback } from '../models/event';
import { ApplicationConfiguration, ApplicationDto, InstanceNodeConfiguration, InstanceNodeConfigurationDto } from '../models/gen.dtos';
import { EditAppConfigContext, ProcessConfigDto } from '../models/process.model';
import { ApplicationService } from '../services/application.service';
import { getAppOs } from '../utils/manifest.utils';

@Component({
  selector: 'app-instance-node-card',
  templateUrl: './instance-node-card.component.html',
  styleUrls: ['./instance-node-card.component.css'],
})
export class InstanceNodeCardComponent implements OnInit, OnDestroy {
  /* CSS Classes attached to nodes to highligh valid / invalid drop zones */
  private readonly VALID_DROP_ZONE_CLASS = 'instance-node-valid-drop-zone';
  private readonly INVALID_DROP_ZONE_CLASS = 'instance-node-invalid-drop-zone';
  private readonly CURRENT_DRAG_CONTAINER_CLASS = 'current-drag-container';

  @Input() instanceGroupName: string;
  @Input() activatedInstanceTag: string;
  @Input() processConfig: ProcessConfigDto;
  @Input() productMissing: boolean;
  @Input() node: InstanceNodeConfigurationDto;
  @Input() manageApplications: boolean;
  @Input() isReadonly: boolean;

  @Output() editAppConfigEvent = new EventEmitter<EditAppConfigContext>();
  @Output() editNodeAppsEvent = new EventEmitter<void>();
  @Output() removeNodeAppEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() selectAppConfigEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() downloadClickAndStartEvent = new EventEmitter<ApplicationConfiguration>();
  @Output() downloadInstallerEvent = new EventEmitter<EventWithCallback<ApplicationConfiguration>>();

  @ViewChild('appNodeCard', { read: ElementRef, static: false })
  appNodeCard: ElementRef;

  @ViewChild('dragulaContainer', { read: ElementRef, static: false })
  dragulaContainer: ElementRef;

  private nodeConfigCreated = false;
  private subscription: Subscription;

  public nodeApps: ApplicationConfiguration[] = [];

  constructor(private appService: ApplicationService, private dragulaService: DragulaService) { }

  ngOnInit() {
    this.subscription = new Subscription();
    this.nodeApps = this.node.nodeConfiguration ? this.node.nodeConfiguration.applications : [];

    // Handle dropping of applications
    this.subscription.add(this.dragulaService.dropModel().subscribe(({ target, source, item, sourceIndex, targetIndex }) => {
      this.onDrop(target, source, item, sourceIndex, targetIndex);
    }));

    // Visualize valid targets for dropping elements
    this.subscription.add(this.dragulaService.drag().subscribe(( { el }) => {
      this.onDrag(el);
    }));

    // Remove visualization of valid targets for dropping elements
    this.subscription.add(this.dragulaService.dragend().subscribe(( {} ) => {
      this.onDragEnd();
    }));

    this.subscription.add(this.dragulaService.over().subscribe(({ container }) => {
      this.onDragOver(container);
    }));

    this.subscription.add(this.dragulaService.out().subscribe(({ container }) => {
      this.onDragOut(container);
    }));
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  /** Called when the drag ended (canceled or success) */
  private onDragEnd() {
    const cardClassList = this.appNodeCard.nativeElement.classList;
    cardClassList.remove(this.VALID_DROP_ZONE_CLASS);
    cardClassList.remove(this.INVALID_DROP_ZONE_CLASS);
    cardClassList.remove(this.CURRENT_DRAG_CONTAINER_CLASS);
  }

  /** Called when a drag operation started */
  private onDrag(el: Element) {
    const cardClassList = this.appNodeCard.nativeElement.classList;
    const dropContainer = this.dragulaContainer.nativeElement;
    if (this.appService.isAppCompatibleWithNode(el, dropContainer)) {
      cardClassList.remove(this.INVALID_DROP_ZONE_CLASS);
      cardClassList.add(this.VALID_DROP_ZONE_CLASS);
    } else {
      cardClassList.remove(this.VALID_DROP_ZONE_CLASS);
      cardClassList.add(this.INVALID_DROP_ZONE_CLASS);
    }
  }

  private onDragOver(target: Element) {
    const id = 'dragula-nodeName-' + this.node.nodeName;
    const isThisNodeTheTarget = target.className.includes(id);
    if (isThisNodeTheTarget) {
      this.appNodeCard.nativeElement.classList.add(this.CURRENT_DRAG_CONTAINER_CLASS);
    }
  }

  private onDragOut(target: Element) {
    const id = 'dragula-nodeName-' + this.node.nodeName;
    const isThisNodeTheTarget = target.className.includes(id);
    if (isThisNodeTheTarget) {
      this.appNodeCard.nativeElement.classList.remove(this.CURRENT_DRAG_CONTAINER_CLASS);
    }
  }

  /** Called when an application is dropped */
  private onDrop(target: Element, source: Element, item: any, sourceIndex: number, targetIndex: number) {
    const id = 'dragula-nodeName-' + this.node.nodeName;
    const isThisNodeTheTarget = target.className.includes(id);
    const isThisNodeTheSource = source.className.includes(id);
    if (isThisNodeTheSource && isThisNodeTheTarget) {
      // Re-Arrange within the same node
      this.moveProcess(item, sourceIndex, targetIndex);
    } else if (isThisNodeTheTarget) {
      // Add process if we are the target
      this.addProcess(item, targetIndex);
    } else if (isThisNodeTheSource) {
      // Remove process if we are the source
      this.removeProcess(item, sourceIndex);
    }
  }

  /**
   * Called when the user re-arranges an existing application within the same node
   */
  private moveProcess(data: any, sourceIndex: number, targetIndex: number) {
    // Move within the array
    moveItemInArray(this.nodeApps, sourceIndex, targetIndex);

    // Notify about the change
    this.editNodeAppsEvent.emit();
  }

  /**
   * Called when the user drops an existing application on this node or
   * when the user drops a new application group from the sidebar
   */
  private addProcess(data: any, targetIndex: number) {
    if (!this.node.nodeConfiguration) {
      this.createNewNodeConfig();
    }

    // Create configurations for all supported OS
    if (data instanceof ApplicationGroup) {
      const group = data as ApplicationGroup;
      if (this.isClientApplicationsNode()) {
        for (const app of group.applications) {
          const newCfg = this.createNewAppConfig(app);
          this.nodeApps.splice(targetIndex, 0, newCfg);
        }
      } else {
        const nodeOs = this.node.status.os;
        const app = group.getAppFor(nodeOs);
        const newCfg = this.createNewAppConfig(app);
        this.nodeApps.splice(targetIndex, 0, newCfg);
      }
    } else {
      // Simply add the given app to our list of applications
      this.nodeApps.splice(targetIndex, 0, data);
    }

    // Notify about the change
    this.editNodeAppsEvent.emit();
  }

  /**
   * Called when the user drags the application from one node to another node.
   */
  private removeProcess(application: ApplicationConfiguration, sourceIndex: number) {
    const index = this.nodeApps.indexOf(application);
    if (index === -1) {
      return;
    }
    this.nodeApps.splice(index, 1);

    // Clear node config when deleting the last app to
    // Ensure we are back in a non-modified state
    if (this.nodeApps.length === 0 && this.nodeConfigCreated) {
      this.node.nodeConfiguration = null;
      this.nodeConfigCreated = false;
    }

    // Notify about the change
    this.editNodeAppsEvent.emit();
    this.removeNodeAppEvent.emit(application);
  }

  onSelect(process: ApplicationConfiguration): void {
    this.selectAppConfigEvent.emit(process);
  }

  fireEditAppConfigEvent(appConfig: ApplicationConfiguration) {
    this.editAppConfigEvent.emit(new EditAppConfigContext(this.node, appConfig));
  }

  /** Returns whether or not at least one app has been added to the node */
  hasApps() {
    return this.nodeApps.length > 0;
  }

  /** Creates a new node configuration and initializes it with default value */
  createNewNodeConfig() {
    this.nodeConfigCreated = true;
    this.node.nodeConfiguration = cloneDeep(EMPTY_INSTANCE_NODE_CONFIGURATION);
    this.node.nodeConfiguration.uuid = this.processConfig.instance.uuid;
    this.node.nodeConfiguration.name = this.processConfig.instance.name;
    this.node.nodeConfiguration.autoStart = true;
    this.nodeApps = this.node.nodeConfiguration.applications;
  }

  /** Creates a new application configuration and initializes it with default values */
  createNewAppConfig(app: ApplicationDto): ApplicationConfiguration {
    const appConfig = cloneDeep(EMPTY_APPLICATION_CONFIGURATION);
    appConfig.processControl = cloneDeep(EMPTY_PROCESS_CONTROL_CONFIG);
    appConfig.application = app.key;
    appConfig.name = app.name;

    // default process control configuration
    const processControlDesc = app.descriptor.processControl;
    const processControlConfig = appConfig.processControl;
    processControlConfig.gracePeriod = processControlDesc.gracePeriod;
    if (processControlDesc.supportedStartTypes) {
      processControlConfig.startType = processControlDesc.supportedStartTypes[0];
    }
    processControlConfig.keepAlive = processControlDesc.supportsKeepAlive;
    processControlConfig.noOfRetries = processControlDesc.noOfRetries;

    // Lookup parameter in all available applications
    const apps = this.appService.getAllApps(this.processConfig);

    // Load descriptor and initialize configuration
    const productKey = this.processConfig.instance.product;
    const appKey = appConfig.application;
    this.appService.getDescriptor(this.instanceGroupName, productKey, appKey).subscribe(desc => {
      // Generate unique identifier
      this.appService.createUuid(this.instanceGroupName).subscribe(uid => {
        appConfig.uid = uid;
        this.appService.initAppConfig(appConfig, desc, apps);
        this.editNodeAppsEvent.emit();
      });
    });
    return appConfig;
  }

  countForeignApps(foreign: InstanceNodeConfiguration[]): number {
    let count = 0;

    if (!foreign || foreign.length === 0) {
      return count;
    }

    for (const x of foreign) {
      count += x.applications.length;
    }

    return count;
  }

  getForeignInstanceText(): string {
    const count = this.countForeignApps(this.node.foreignNodeConfigurations);
    const appPlural = count === 1 ? '' : 's';
    const instanceCount = this.node.foreignNodeConfigurations.length;
    const instancePlural = instanceCount === 1 ? '' : 's';
    const isAre = count === 1 ? 'is' : 'are';

    return `${count} application${appPlural} from ${instanceCount} instance${instancePlural} ${isAre} configured for this node.`;
  }

  isClientApplicationsNode(): boolean {
    return this.node.nodeName === CLIENT_NODE_NAME;
  }

  /**
   * Returns the drag&drop specific classes that are used to determine what can be dropped on this node.
   */
  getDragulaNodeClasses(): string[] {
    const classes: string[] = [];

    // when editing, set a minimum size here.
    if (this.manageApplications) {
      classes.push('init-zone');
    }

    // Name is used to identify source and target node during drop handling
    classes.push('dragula-nodeName-' + this.node.nodeName);

    // Append node type
    if (this.isClientApplicationsNode()) {
      classes.push('dragula-nodeType-client');
    } else {
      classes.push('dragula-nodeType-server');
    }

    // Append node OS - only server nodes have a state
    if (!this.isClientApplicationsNode()) {
      if (this.node.status) {
        classes.push('dragula-nodeOs-' + this.node.status.os.toLowerCase());
      } else {
        classes.push('dragula-nodeOs-offline');
      }
    }

    return classes;
  }

  /**
   * Returns the drag&drop specific classes that are used to determine where a specific app can be moved to.
   */
  getDragulaAppClasses(appConfig: ApplicationConfiguration): string[] {
    const classes: string[] = [];

    // Name is used to identify source and target node during drop handling
    classes.push('dragula-nodeName-' + this.node.nodeName);

    // Append app type
    if (this.isClientApplicationsNode()) {
      classes.push('dragula-appType-client');
    } else {
      classes.push('dragula-appType-server');
    }

    // Append OS of app
    const appOs = getAppOs(appConfig.application);
    classes.push('dragula-appOs-' + appOs.toLowerCase());
    return classes;
  }
}
