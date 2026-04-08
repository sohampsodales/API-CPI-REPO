import { NavLink, Outlet } from "react-router-dom";
import logo from "../assets/sodales-Logos_White.png";
import "../styles/global.css";

export default function MainLayout() {
  return (
    <div className="appShell">
      <div className="navbar">
        <div className="nav-left">
          <div className="header">
            <img src={logo} alt="Logo" className="logo" />
          </div>
          <span className="nav-title">QA Utilities For Testing </span>
        </div>

        <nav className="nav-right">
          <NavLink
            to="/api-testing"
            className={({ isActive }) =>
              `navTab ${isActive ? "navTabActive" : ""}`
            }
          >
            API Testing
          </NavLink>

          <NavLink
            to="/reports"
            className={({ isActive }) =>
              `navTab ${isActive ? "navTabActive" : ""}`
            }
          >
            Further Tab 1
          </NavLink>

          <NavLink
            to="/settings"
            className={({ isActive }) =>
              `navTab ${isActive ? "navTabActive" : ""}`
            }
          >
            Further Tab 2
          </NavLink>
        </nav>
      </div>

      <main className="mainContent">
        <Outlet />
      </main>
    </div>
  );
}
