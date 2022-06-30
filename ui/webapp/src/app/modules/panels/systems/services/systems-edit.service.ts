import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, combineLatest } from 'rxjs';
import { SystemConfigurationDto } from 'src/app/models/gen.dtos';
import { ConfigService } from 'src/app/modules/core/services/config.service';
import { NavAreasService } from 'src/app/modules/core/services/nav-areas.service';
import { measure } from 'src/app/modules/core/utils/performance.utils';
import { GroupsService } from 'src/app/modules/primary/groups/services/groups.service';
import { SystemsService } from 'src/app/modules/primary/systems/services/systems.service';

@Injectable({
  providedIn: 'root',
})
export class SystemsEditService {
  public current$ = new BehaviorSubject<SystemConfigurationDto>(null);
  private apiPath = (g) => `${this.cfg.config.api}/group/${g}/system`;

  constructor(
    private cfg: ConfigService,
    private http: HttpClient,
    private groups: GroupsService,
    nav: NavAreasService,
    systems: SystemsService
  ) {
    combineLatest([systems.systems$, nav.panelRoute$]).subscribe(
      ([systems, panelRoute]) => {
        if (!systems?.length || !panelRoute?.params?.skey) {
          this.current$.next(null);
          return;
        }
        this.current$.next(
          systems.find((s) => s.key.name === panelRoute.params.skey)
        );
      }
    );
  }

  public update(system: SystemConfigurationDto) {
    return this.http
      .post(`${this.apiPath(this.groups.current$?.value.name)}`, system)
      .pipe(measure(`Update system ${system.config.name}`));
  }
}
