import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, ReplaySubject, share } from 'rxjs';

interface ReplayCache {
  [someUrl: string]: Observable<any>;
}

@Injectable({
  providedIn: 'root',
})
export class HttpReplayService {
  private http = inject(HttpClient);

  private cache: ReplayCache = {};

  public get<T>(url: string, resetTimeMs = 1000): Observable<T> {
    if (!this.cache[url]) {
      this.cache[url] = this.http.get<T>(url).pipe(
        share({
          connector: () => new ReplaySubject(1),
          resetOnError: true,
          resetOnComplete: false,
          resetOnRefCountZero: false,
        })
      );
      setTimeout(() => delete this.cache[url], resetTimeMs);
    }
    return this.cache[url];
  }
}
