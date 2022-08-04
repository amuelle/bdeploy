import { ConnectedPosition, Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import {
  Directive,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnDestroy,
  Output,
  Renderer2,
  TemplateRef,
  ViewContainerRef,
} from '@angular/core';
import { ThemePalette } from '@angular/material/core';
import { cloneDeep } from 'lodash-es';
import { PopupService } from '../../services/popup.service';

/**
 * Popup preferred position.
 *
 * "Primary" (first part) dictates on which side of the button the popup will be placed.
 *
 * "Secondary" (second part) dictates the alignment of the popup relative to the button.
 *
 * Example: 'below-left' will place the popup below the button, _then_ align its right side
 * to the right side of the button, so that the popup will *extend* to the left.
 */
export type PopupPosition =
  | 'below-left'
  | 'below-right'
  | 'above-left'
  | 'above-right'
  | 'left-above'
  | 'left-below'
  | 'right-above'
  | 'right-below';

/**
 * minimum distance to the edge of the viewport in any direction.
 * this needs to be accounted for in position strategies on the X axis.
 */
const VIEWPORT_MARGIN = 10;

const BELOW_LEFT: ConnectedPosition = {
  originX: 'end',
  originY: 'bottom',
  overlayX: 'end',
  overlayY: 'top',
  offsetY: 10,
  offsetX: -VIEWPORT_MARGIN,
  panelClass: 'bd-popup-panel-below-left',
};
const BELOW_RIGHT: ConnectedPosition = {
  originX: 'start',
  originY: 'bottom',
  overlayX: 'start',
  overlayY: 'top',
  offsetY: 10,
  panelClass: 'bd-popup-panel-below-right',
};
const ABOVE_LEFT: ConnectedPosition = {
  originX: 'end',
  originY: 'top',
  overlayX: 'end',
  overlayY: 'bottom',
  offsetY: -10,
  offsetX: -VIEWPORT_MARGIN,
  panelClass: 'bd-popup-panel-above-left',
};
const ABOVE_RIGHT: ConnectedPosition = {
  originX: 'start',
  originY: 'top',
  overlayX: 'start',
  overlayY: 'bottom',
  offsetY: -10,
  panelClass: 'bd-popup-panel-above-right',
};
const LEFT_ABOVE: ConnectedPosition = {
  originX: 'start',
  originY: 'bottom',
  overlayX: 'end',
  overlayY: 'bottom',
  offsetX: -10 - VIEWPORT_MARGIN,
  panelClass: 'bd-popup-panel-left-above',
};
const LEFT_BELOW: ConnectedPosition = {
  originX: 'start',
  originY: 'top',
  overlayX: 'end',
  overlayY: 'top',
  offsetX: -10 - VIEWPORT_MARGIN,
  panelClass: 'bd-popup-panel-left-below',
};
const RIGHT_ABOVE: ConnectedPosition = {
  originX: 'end',
  originY: 'bottom',
  overlayX: 'start',
  overlayY: 'bottom',
  offsetX: 10,
  panelClass: 'bd-popup-panel-right-above',
};
const RIGHT_BELOW: ConnectedPosition = {
  originX: 'end',
  originY: 'top',
  overlayX: 'start',
  overlayY: 'top',
  offsetX: 10,
  panelClass: 'bd-popup-panel-right-below',
};

@Directive({
  selector: '[appBdPopup]',
  exportAs: 'appBdPopup',
})
export class BdPopupDirective implements OnDestroy {
  @Input() appBdPopup: TemplateRef<any>;
  @Input() appBdPopupTrigger: 'click' | 'hover' = 'click';
  @Input() appBdPopupDelay = 0;

  @Input() appBdPopupPosition: PopupPosition = 'below-left';
  @Input() appBdPopupBackdropClass: string;
  @Input() appBdPopupChevronColor: ThemePalette;

  @Output() appBdPopupOpened = new EventEmitter<BdPopupDirective>();

  private delayTimer;
  private overlayRef: OverlayRef;
  private canClosePopover = false;

  constructor(
    private host: ElementRef,
    private overlay: Overlay,
    private viewContainerRef: ViewContainerRef,
    private popupService: PopupService,
    private _render: Renderer2
  ) {}

  ngOnDestroy(): void {
    this.closeOverlay();
  }

  @HostListener('mouseenter') onMouseEnter() {
    if (
      this.appBdPopupTrigger === 'hover' &&
      !!this.appBdPopup &&
      !this.popupService.getOverlay('click') // only if no click-overlay is open
    ) {
      this.delayTimer = setTimeout(() => {
        this.canClosePopover = true;
        this.openOverlay();
        setTimeout(() => {
          this.keepPopupOpen();
        }, 100);
      }, this.appBdPopupDelay);
    }
  }

  @HostListener('mouseleave') onMouseLeave() {
    if (this.appBdPopupTrigger === 'hover' && !!this.appBdPopup) {
      setTimeout(() => {
        if (this.canClosePopover) {
          clearTimeout(this.delayTimer);
          this.closeOverlay();
        }
      }, 100);
    }
  }

  @HostListener('click') onMouseClick() {
    if (this.appBdPopupTrigger !== 'click' || !this.appBdPopup) {
      return;
    }

    if (this.overlayRef) {
      this.closeOverlay();
    } else {
      this.openOverlay();
    }
  }

  /** Opens a modal overlay popup showing the given template */
  public openOverlay() {
    this.closeOverlay();

    this.overlayRef = this.overlay.create({
      positionStrategy: this.overlay
        .position()
        .flexibleConnectedTo(this.host)
        .withPositions(this.fixupPanelClasses(this.getPositions()))
        .withPush(false)
        .withViewportMargin(VIEWPORT_MARGIN),
      scrollStrategy: this.overlay.scrollStrategies.close(),
      hasBackdrop: this.appBdPopupTrigger === 'click',
      backdropClass: this.appBdPopupBackdropClass,
      disposeOnNavigation: true,
    });
    this.overlayRef.backdropClick().subscribe(() => this.closeOverlay());

    const portal = new TemplatePortal(this.appBdPopup, this.viewContainerRef);
    this.overlayRef.attach(portal);

    this.popupService.setOverlay(this.overlayRef, this.appBdPopupTrigger);

    this.appBdPopupOpened.emit(this);
  }

  private getPositions(): ConnectedPosition[] {
    // preferred positions from most to least preferred depending on given most-preferred position.
    // preferred placement has a "primary" and a "secondary" component. The primary determined on which
    // side of the button the popup is attached. The secondary determines which side of the popup is
    // attached to the button.
    // the logic is to always first try to alternate the "secondary" placement, and then the "primary"
    // if there is not enough room to fit it to the preferred position.
    switch (this.appBdPopupPosition) {
      case 'above-left':
        return [ABOVE_LEFT, ABOVE_RIGHT, BELOW_LEFT];
      case 'above-right':
        return [ABOVE_RIGHT, ABOVE_LEFT, BELOW_RIGHT];
      case 'below-left':
        return [BELOW_LEFT, BELOW_RIGHT, ABOVE_LEFT];
      case 'below-right':
        return [BELOW_RIGHT, BELOW_LEFT, ABOVE_RIGHT];
      case 'left-above':
        return [LEFT_ABOVE, LEFT_BELOW, RIGHT_ABOVE];
      case 'left-below':
        return [LEFT_BELOW, LEFT_ABOVE, RIGHT_BELOW];
      case 'right-above':
        return [RIGHT_ABOVE, RIGHT_BELOW, LEFT_ABOVE];
      case 'right-below':
        return [RIGHT_BELOW, RIGHT_ABOVE, LEFT_BELOW];
    }
  }

  private fixupPanelClasses(pos: ConnectedPosition[]) {
    const name = this.appBdPopupChevronColor
      ? this.appBdPopupChevronColor
      : 'default';
    const result = [];
    pos.forEach((p) => {
      const x = cloneDeep(p);
      x.panelClass = x.panelClass + '-' + name;
      result.push(x);
    });
    return result;
  }

  /** Closes the overlay if present */
  public closeOverlay() {
    if (this.popupService.getOverlay(this.appBdPopupTrigger)) {
      this.overlayRef = this.popupService.getOverlay(this.appBdPopupTrigger);
      this.overlayRef.detach();
      this.overlayRef.dispose();
      this.overlayRef = null;
    }
    this.popupService.setOverlay(this.overlayRef, this.appBdPopupTrigger);
  }

  /** Keeps popup open if mouse is over of popup */
  keepPopupOpen() {
    const tempClasses = this.fixupPanelClasses(this.getPositions());

    tempClasses.forEach((item) => {
      const popover = window.document.querySelector(`.${item.panelClass}`);
      if (popover) {
        this._render.listen(popover, 'mouseover', () => {
          this.canClosePopover = false;
        });

        this._render.listen(popover, 'mouseout', () => {
          this.canClosePopover = true;
          setTimeout(() => {
            if (this.canClosePopover) this.closeOverlay();
          }, 0);
        });
      }
    });
  }
}
